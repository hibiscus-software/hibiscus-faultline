/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.build;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;

/**
 * The server's answer to a preview request: whether the operation could go ahead, what it needs, and which positions
 * it would skip.
 *
 * @param problem  why the operation can't run at all, if it can't
 * @param skipped  packed positions ({@link net.minecraft.core.BlockPos#asLong}) the operation would leave alone
 *                 because they are protected, unbreakable or can't be paid for; capped at the ghost preview limit
 */
public record BuildPreview(int requestId, Optional<Component> problem, long volume, List<MaterialLine> materials, long[] skipped) {
    public static final StreamCodec<RegistryFriendlyByteBuf, BuildPreview> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BuildPreview::requestId,
            ComponentSerialization.OPTIONAL_STREAM_CODEC, BuildPreview::problem,
            ByteBufCodecs.VAR_LONG, BuildPreview::volume,
            MaterialLine.STREAM_CODEC.apply(ByteBufCodecs.list()), BuildPreview::materials,
            ByteBufCodecs.LONG_ARRAY, BuildPreview::skipped,
            BuildPreview::new);

    /** One item on the bill of materials: how many the operation uses, and how many the player has. */
    public record MaterialLine(Holder<Item> item, int needed, int have) {
        public static final StreamCodec<RegistryFriendlyByteBuf, MaterialLine> STREAM_CODEC = StreamCodec.composite(
                Item.STREAM_CODEC, MaterialLine::item,
                ByteBufCodecs.VAR_INT, MaterialLine::needed,
                ByteBufCodecs.VAR_INT, MaterialLine::have,
                MaterialLine::new);

        public boolean isShort() {
            return have < needed;
        }
    }
}
