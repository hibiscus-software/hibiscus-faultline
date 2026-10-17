/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodConstants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import software.hibiscus.hibiscusfaultline.build.BuildBox;
import software.hibiscus.hibiscusfaultline.build.BuildOption;
import software.hibiscus.hibiscusfaultline.build.BuildRequest;
import software.hibiscus.hibiscusfaultline.build.BuildTool;
import software.hibiscus.hibiscusfaultline.build.BuildTransform;
import software.hibiscus.hibiscusfaultline.config.ServerConfig;
import software.hibiscus.hibiscusfaultline.server.BuildJobs;
import software.hibiscus.hibiscusfaultline.server.BuildValidator;
import software.hibiscus.hibiscusfaultline.server.MiningCosts;
import org.jspecify.annotations.Nullable;

/**
 * Erase and Set against a real server world with a mock player. Each test builds its blocks around the test origin,
 * runs the operation through {@link BuildJobs} exactly as a confirm from the client would, and removes its player at
 * the end.
 */
final class BuildToolTests {
    /** Positions a test wants protection to refuse; a listener registered with the tests cancels breaks there. */
    private static final Set<BlockPos> DENIED = new HashSet<>();
    private static final float EPSILON = 1.0E-4F;

    private BuildToolTests() {}

    static void denyBreaks(BreakBlockEvent event) {
        if (DENIED.contains(event.getPos())) {
            event.setCanceled(true);
        }
    }

    static void eraseDropsToInventory(GameTestHelper helper) {
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        row(helper, Blocks.DIRT, 3);
        start(helper, player, request(helper, BuildTool.ERASE, 0, 0, 2, 0, null));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    assertRow(helper, Blocks.AIR, 3);
                    helper.assertValueEqual(player.getInventory().countItem(Items.DIRT), 3, "dirt in inventory");
                })
                .thenExecute(() -> remove(player))
                .thenSucceed();
    }

    static void eraseSkipsUnharvestable(GameTestHelper helper) {
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        helper.setBlock(0, 1, 0, Blocks.DIRT);
        helper.setBlock(1, 1, 0, Blocks.STONE);
        start(helper, player, request(helper, BuildTool.ERASE, 0, 0, 1, 0, null));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(0, 1, 0));
                    helper.assertTrue(BuildJobs.get(player) == null, "the job should have finished");
                })
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.STONE, new BlockPos(1, 1, 0));
                    helper.assertValueEqual(player.getInventory().countItem(Items.COBBLESTONE), 0, "cobblestone in inventory");
                    remove(player);
                })
                .thenSucceed();
    }

    static void eraseHungerMatchesVeinMining(GameTestHelper helper) {
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        row(helper, Blocks.DIRT, 3);
        float perBlock = FoodConstants.EXHAUSTION_MINE + ServerConfig.settings().hungerPerBlock();
        start(helper, player, request(helper, BuildTool.ERASE, 0, 0, 2, 0, null));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BuildJobs.get(player) == null, "the job should have finished"))
                .thenExecute(() -> {
                    float spent = exhaustion(player);
                    helper.assertTrue(Math.abs(spent - 3 * perBlock) < EPSILON,
                            "exhaustion " + spent + " should be 3 blocks at " + perBlock + ", the same as vein mining");
                    remove(player);
                })
                .thenSucceed();
    }

    static void eraseStopsWhenHungry(GameTestHelper helper) {
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        player.getFoodData().setFoodLevel(0);
        player.getFoodData().setSaturation(0);
        row(helper, Blocks.DIRT, 3);
        start(helper, player, request(helper, BuildTool.ERASE, 0, 0, 2, 0, null));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BuildJobs.get(player) == null, "the job should have stopped"))
                .thenExecute(() -> {
                    assertRow(helper, Blocks.DIRT, 3);
                    remove(player);
                })
                .thenSucceed();
    }

    /** Obsidian takes far longer than dirt, and a better pickaxe is faster on stone. */
    static void eraseTimeScales(GameTestHelper helper) {
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        ItemStack diamond = new ItemStack(Items.DIAMOND_PICKAXE);
        int dirt = MiningCosts.breakTicks(player, diamond, Blocks.DIRT.defaultBlockState(), helper.getLevel(), pos, 1.0F);
        int obsidian = MiningCosts.breakTicks(player, diamond, Blocks.OBSIDIAN.defaultBlockState(), helper.getLevel(), pos, 1.0F);
        int stoneWood = MiningCosts.breakTicks(player, new ItemStack(Items.WOODEN_PICKAXE), Blocks.STONE.defaultBlockState(), helper.getLevel(), pos, 1.0F);
        int stoneIron = MiningCosts.breakTicks(player, new ItemStack(Items.IRON_PICKAXE), Blocks.STONE.defaultBlockState(), helper.getLevel(), pos, 1.0F);
        int halved = MiningCosts.breakTicks(player, diamond, Blocks.OBSIDIAN.defaultBlockState(), helper.getLevel(), pos, 0.5F);
        remove(player);
        helper.assertTrue(obsidian > dirt * 10, "obsidian (" + obsidian + ") should take much longer than dirt (" + dirt + ")");
        helper.assertTrue(stoneIron < stoneWood, "an iron pickaxe (" + stoneIron + ") should beat a wooden one (" + stoneWood + ")");
        helper.assertTrue(Math.abs(halved - obsidian / 2) <= 1, "the multiplier should scale the time");
        helper.succeed();
    }

    static void eraseContainerContentsReturned(GameTestHelper helper) {
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        helper.setBlock(0, 1, 0, Blocks.CHEST);
        if (helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(0, 1, 0))) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.DIAMOND, 5));
        }
        start(helper, player, request(helper, BuildTool.ERASE, 0, 0, 0, 0, null));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(0, 1, 0));
                    helper.assertValueEqual(player.getInventory().countItem(Items.DIAMOND), 5, "diamonds from the chest");
                    helper.assertValueEqual(player.getInventory().countItem(Items.CHEST), 1, "the chest itself");
                })
                .thenExecute(() -> {
                    helper.assertTrue(itemsAround(helper) == 0, "nothing should have spilled into the world");
                    remove(player);
                })
                .thenSucceed();
    }

    static void neverTouchesImmovable(GameTestHelper helper) {
        ServerPlayer player = player(helper, GameType.CREATIVE);
        helper.setBlock(0, 1, 0, Blocks.BEDROCK);
        helper.setBlock(1, 1, 0, Blocks.SPAWNER);
        helper.setBlock(2, 1, 0, Blocks.DIRT);
        start(helper, player, request(helper, BuildTool.ERASE, 0, 0, 2, 0, null));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 1, 0));
        helper.assertBlockPresent(Blocks.BEDROCK, new BlockPos(0, 1, 0));
        helper.assertBlockPresent(Blocks.SPAWNER, new BlockPos(1, 1, 0));
        remove(player);
        helper.succeed();
    }

    static void protectionSkipsDenied(GameTestHelper helper) {
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        row(helper, Blocks.DIRT, 2);
        BlockPos denied = helper.absolutePos(new BlockPos(1, 1, 0));
        DENIED.add(denied);
        start(helper, player, request(helper, BuildTool.ERASE, 0, 0, 1, 0, null));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BuildJobs.get(player) == null, "the job should have finished"))
                .thenExecute(() -> {
                    DENIED.remove(denied);
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(0, 1, 0));
                    helper.assertBlockPresent(Blocks.DIRT, new BlockPos(1, 1, 0));
                    remove(player);
                })
                .thenSucceed();
    }

    static void setAirOnly(GameTestHelper helper) {
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        player.getInventory().add(new ItemStack(Items.DIRT, 3));
        helper.setBlock(1, 1, 0, Blocks.STONE);
        start(helper, player, request(helper, BuildTool.SET, 0, 0, 2, 0, Blocks.DIRT, BuildOption.AIR_ONLY));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertBlockPresent(Blocks.DIRT, new BlockPos(0, 1, 0));
                    helper.assertBlockPresent(Blocks.DIRT, new BlockPos(2, 1, 0));
                })
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.STONE, new BlockPos(1, 1, 0));
                    helper.assertValueEqual(player.getInventory().countItem(Items.DIRT), 1, "dirt left over");
                    remove(player);
                })
                .thenSucceed();
    }

    static void setRefusesWhenShort(GameTestHelper helper) {
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        player.getInventory().add(new ItemStack(Items.DIRT, 2));
        Optional<Component> problem = BuildJobs.start(player, request(helper, BuildTool.SET, 0, 0, 2, 0, Blocks.DIRT));
        helper.assertTrue(problem.isPresent(), "Set should refuse without enough dirt");
        assertRow(helper, Blocks.AIR, 3);
        helper.assertValueEqual(player.getInventory().countItem(Items.DIRT), 2, "dirt untouched");
        remove(player);
        helper.succeed();
    }

    static void setPartialBottomUp(GameTestHelper helper) {
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        player.getInventory().add(new ItemStack(Items.DIRT, 2));
        start(helper, player, new BuildRequest(BuildTool.SET, mask(BuildOption.PARTIAL),
                BuildBox.of(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(0, 3, 0))),
                BuildTransform.IDENTITY, Optional.of(Blocks.DIRT.builtInRegistryHolder()), Optional.empty()));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BuildJobs.get(player) == null, "the job should have finished"))
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.DIRT, new BlockPos(0, 1, 0));
                    helper.assertBlockPresent(Blocks.DIRT, new BlockPos(0, 2, 0));
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(0, 3, 0));
                    helper.assertValueEqual(player.getInventory().countItem(Items.DIRT), 0, "all dirt used");
                    remove(player);
                })
                .thenSucceed();
    }

    /** Set over stone breaks it first: drops, the same hunger as Erase, and at least the stone's mining time. */
    static void setOverOccupiedPays(GameTestHelper helper) {
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        ItemStack pickaxe = new ItemStack(Items.STONE_PICKAXE);
        player.getInventory().add(pickaxe.copy());
        player.getInventory().add(new ItemStack(Items.DIRT, 2));
        row(helper, Blocks.STONE, 2);
        int minTicks = MiningCosts.breakTicks(player, pickaxe, Blocks.STONE.defaultBlockState(), helper.getLevel(),
                helper.absolutePos(new BlockPos(0, 1, 0)), ServerConfig.buildEraseTimeMultiplier());
        float perBlock = FoodConstants.EXHAUSTION_MINE + ServerConfig.settings().hungerPerBlock();
        long startTick = helper.getTick();
        start(helper, player, request(helper, BuildTool.SET, 0, 0, 1, 0, Blocks.DIRT));
        helper.startSequence()
                .thenWaitUntil(() -> assertRow(helper, Blocks.DIRT, 2))
                .thenExecute(() -> {
                    // The job's first tick runs inside start(), in the tick the test began.
                    helper.assertTrue(helper.getTick() - startTick + 1 >= minTicks, "breaking the stone should take at least " + minTicks + " ticks");
                    helper.assertValueEqual(player.getInventory().countItem(Items.COBBLESTONE), 2, "cobblestone returned");
                    float spent = exhaustion(player);
                    helper.assertTrue(Math.abs(spent - 2 * perBlock) < EPSILON, "exhaustion " + spent + " should be 2 blocks at " + perBlock);
                    remove(player);
                })
                .thenSucceed();
    }

    static void creativeSetFree(GameTestHelper helper) {
        ServerPlayer player = player(helper, GameType.CREATIVE);
        start(helper, player, request(helper, BuildTool.SET, 0, 0, 2, 0, Blocks.DIRT));
        assertRow(helper, Blocks.DIRT, 3);
        helper.assertTrue(player.getInventory().isEmpty(), "creative Set should use no items");
        remove(player);
        helper.succeed();
    }

    static void creativeEraseInstant(GameTestHelper helper) {
        ServerPlayer player = player(helper, GameType.CREATIVE);
        row(helper, Blocks.DIRT, 2);
        helper.setBlock(2, 1, 0, Blocks.CHEST);
        if (helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(2, 1, 0))) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.DIAMOND, 5));
        }
        start(helper, player, request(helper, BuildTool.ERASE, 0, 0, 2, 0, null));
        assertRow(helper, Blocks.AIR, 3);
        helper.assertTrue(BuildJobs.get(player) == null, "creative Erase should finish in the tick it starts");
        helper.assertTrue(player.getInventory().isEmpty(), "creative Erase should give nothing");
        helper.assertTrue(exhaustion(player) == 0, "creative Erase should cost no hunger");
        helper.assertTrue(itemsAround(helper) == 0, "creative Erase should drop nothing, contents included");
        remove(player);
        helper.succeed();
    }

    static void limitsVolume(GameTestHelper helper) {
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        int side = (int) Math.ceil(Math.cbrt(ServerConfig.buildMaxVolume(false))) + 1;
        BuildRequest tooBig = new BuildRequest(BuildTool.ERASE, 0,
                BuildBox.of(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(side - 1, side, side - 1))),
                BuildTransform.IDENTITY, Optional.empty(), Optional.empty());
        BuildRequest fine = request(helper, BuildTool.ERASE, 0, 0, 2, 0, null);
        helper.assertTrue(BuildValidator.checkLimits(player, tooBig, false).isPresent(), "a box over maxVolume should be refused");
        helper.assertTrue(BuildValidator.checkLimits(player, fine, false).isEmpty(), "a small box should pass");
        helper.assertTrue(BuildValidator.maxVolume(player, false) == ServerConfig.buildMaxVolume(false),
                "without a permissions mod the limit comes from the config");
        remove(player);
        helper.succeed();
    }

    // Helpers

    /**
     * A real server player with a mock connection, standing on a block just outside the test area. Also clears the
     * area the tests build in, which the test framework walls with barriers.
     */
    private static ServerPlayer player(GameTestHelper helper, GameType mode) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(3, 4, 1))) {
            helper.setBlock(pos, Blocks.AIR);
        }
        MinecraftServer server = helper.getLevel().getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "faultline-test"), false);
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(mode);
        player.getInventory().clearContent();
        helper.setBlock(0, 1, -3, Blocks.STONE);
        BlockPos feet = helper.absolutePos(new BlockPos(0, 2, -3));
        player.setPos(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        player.setOnGround(true);
        return player;
    }

    private static void remove(ServerPlayer player) {
        BuildJobs.stop(player, null);
        player.level().getServer().getPlayerList().remove(player);
    }

    private static BuildRequest request(GameTestHelper helper, BuildTool tool, int x0, int z0, int x1, int z1,
                                        @Nullable Block block, BuildOption... options) {
        return new BuildRequest(tool, mask(options),
                BuildBox.of(helper.absolutePos(new BlockPos(x0, 1, z0)), helper.absolutePos(new BlockPos(x1, 1, z1))),
                BuildTransform.IDENTITY,
                block == null ? Optional.empty() : Optional.of(block.builtInRegistryHolder()),
                Optional.empty());
    }

    private static int mask(BuildOption... options) {
        int mask = 0;
        for (BuildOption option : options) {
            mask |= 1 << option.ordinal();
        }
        return mask;
    }

    private static void start(GameTestHelper helper, ServerPlayer player, BuildRequest request) {
        Optional<Component> problem = BuildJobs.start(player, request);
        if (problem.isPresent()) {
            remove(player);
            helper.fail(problem.get());
        }
    }

    private static void row(GameTestHelper helper, Block block, int length) {
        for (int x = 0; x < length; x++) {
            helper.setBlock(x, 1, 0, block);
        }
    }

    private static void assertRow(GameTestHelper helper, Block block, int length) {
        for (int x = 0; x < length; x++) {
            helper.assertBlockPresent(block, new BlockPos(x, 1, 0));
        }
    }

    private static float exhaustion(ServerPlayer player) {
        TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        player.getFoodData().addAdditionalSaveData(output);
        CompoundTag tag = output.buildResult();
        return tag.getFloatOr("foodExhaustionLevel", 0);
    }

    private static int itemsAround(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(6)).size();
    }
}
