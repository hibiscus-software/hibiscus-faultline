/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.gametest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import software.hibiscus.hibiscusfaultline.HibiscusFaultline;

/**
 * Registers Faultline's gametests. They run on the gametest server ({@code ./gradlew runGameTestServer}); in a normal
 * game they are registered but never run.
 */
public final class FaultlineGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, HibiscusFaultline.MOD_ID);
    private static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    private static final int MAX_TICKS = 400;
    private static final int PADDING = 8;

    static {
        TESTS.put("erase_drops_to_inventory", BuildToolTests::eraseDropsToInventory);
        TESTS.put("erase_skips_unharvestable", BuildToolTests::eraseSkipsUnharvestable);
        TESTS.put("erase_hunger_matches_vein_mining", BuildToolTests::eraseHungerMatchesVeinMining);
        TESTS.put("erase_stops_when_hungry", BuildToolTests::eraseStopsWhenHungry);
        TESTS.put("erase_time_scales", BuildToolTests::eraseTimeScales);
        TESTS.put("erase_container_contents_returned", BuildToolTests::eraseContainerContentsReturned);
        TESTS.put("never_touches_immovable", BuildToolTests::neverTouchesImmovable);
        TESTS.put("protection_skips_denied", BuildToolTests::protectionSkipsDenied);
        TESTS.put("set_air_only", BuildToolTests::setAirOnly);
        TESTS.put("set_refuses_when_short", BuildToolTests::setRefusesWhenShort);
        TESTS.put("set_partial_bottom_up", BuildToolTests::setPartialBottomUp);
        TESTS.put("set_over_occupied_pays", BuildToolTests::setOverOccupiedPays);
        TESTS.put("creative_set_free", BuildToolTests::creativeSetFree);
        TESTS.put("creative_erase_instant", BuildToolTests::creativeEraseInstant);
        TESTS.put("limits_volume", BuildToolTests::limitsVolume);
        TESTS.put("clone_costs_one_per_block", BuildToolTests::cloneCostsOnePerBlock);
        TESTS.put("clone_refuses_when_short", BuildToolTests::cloneRefusesWhenShort);
        TESTS.put("clone_partial_bottom_up", BuildToolTests::clonePartialBottomUp);
        TESTS.put("clone_chest_arrives_empty", BuildToolTests::cloneChestArrivesEmpty);
        TESTS.put("move_chest_keeps_contents", BuildToolTests::moveChestKeepsContents);
        TESTS.put("move_takes_entities", BuildToolTests::moveTakesEntities);
        TESTS.put("move_rotates_states", BuildToolTests::moveRotatesStates);
        TESTS.put("stack_costs_per_copy", BuildToolTests::stackCostsPerCopy);
        TESTS.forEach((name, test) -> FUNCTIONS.register(name, () -> test));
    }

    private FaultlineGameTests() {}

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(FaultlineGameTests::registerTests);
        NeoForge.EVENT_BUS.addListener(BuildToolTests::denyBreaks);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(HibiscusFaultline.id("default"));
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment, Level.OVERWORLD,
                Identifier.withDefaultNamespace("empty"), MAX_TICKS, 0, true, Rotation.NONE, false, 1, 1, true, PADDING);
        for (String name : TESTS.keySet()) {
            event.registerTest(HibiscusFaultline.id(name), new FunctionGameTestInstance(function(name), data));
        }
    }

    private static ResourceKey<Consumer<GameTestHelper>> function(String name) {
        return ResourceKey.create(Registries.TEST_FUNCTION, HibiscusFaultline.id(name));
    }
}
