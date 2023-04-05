/*
 * This file is part of ViaVersion - https://github.com/ViaVersion/ViaVersion
 * Copyright (C) 2023 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.viaversion.viaversion.protocols.protocol1_20to1_19_4.packets;

import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.ListTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import com.viaversion.viaversion.api.minecraft.blockentity.BlockEntity;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.protocols.protocol1_13to1_12_2.ChatRewriter;
import com.viaversion.viaversion.protocols.protocol1_18to1_17_1.types.Chunk1_18Type;
import com.viaversion.viaversion.protocols.protocol1_19_4to1_19_3.ClientboundPackets1_19_4;
import com.viaversion.viaversion.protocols.protocol1_19_4to1_19_3.ServerboundPackets1_19_4;
import com.viaversion.viaversion.protocols.protocol1_19_4to1_19_3.rewriter.RecipeRewriter1_19_4;
import com.viaversion.viaversion.protocols.protocol1_20to1_19_4.Protocol1_20To1_19_4;
import com.viaversion.viaversion.rewriter.BlockRewriter;
import com.viaversion.viaversion.rewriter.ItemRewriter;
import com.viaversion.viaversion.rewriter.RecipeRewriter;
import com.viaversion.viaversion.util.Key;

public final class InventoryPackets extends ItemRewriter<ClientboundPackets1_19_4, ServerboundPackets1_19_4, Protocol1_20To1_19_4> {

    public InventoryPackets(final Protocol1_20To1_19_4 protocol) {
        super(protocol);
    }

    @Override
    public void registerPackets() {
        final BlockRewriter<ClientboundPackets1_19_4> blockRewriter = new BlockRewriter<>(protocol, Type.POSITION1_14);
        blockRewriter.registerBlockAction(ClientboundPackets1_19_4.BLOCK_ACTION);
        blockRewriter.registerBlockChange(ClientboundPackets1_19_4.BLOCK_CHANGE);
        blockRewriter.registerVarLongMultiBlockChange(ClientboundPackets1_19_4.MULTI_BLOCK_CHANGE);
        blockRewriter.registerEffect(ClientboundPackets1_19_4.EFFECT, 1010, 2001);
        blockRewriter.registerChunkData1_19(ClientboundPackets1_19_4.CHUNK_DATA, Chunk1_18Type::new, this::handleBlockEntity);
        blockRewriter.registerBlockEntityData(ClientboundPackets1_19_4.BLOCK_ENTITY_DATA, this::handleBlockEntity);

        registerOpenWindow(ClientboundPackets1_19_4.OPEN_WINDOW);
        registerSetCooldown(ClientboundPackets1_19_4.COOLDOWN);
        registerWindowItems1_17_1(ClientboundPackets1_19_4.WINDOW_ITEMS);
        registerSetSlot1_17_1(ClientboundPackets1_19_4.SET_SLOT);
        registerAdvancements(ClientboundPackets1_19_4.ADVANCEMENTS, Type.FLAT_VAR_INT_ITEM);
        registerEntityEquipmentArray(ClientboundPackets1_19_4.ENTITY_EQUIPMENT);
        registerClickWindow1_17_1(ServerboundPackets1_19_4.CLICK_WINDOW);
        registerTradeList1_19(ClientboundPackets1_19_4.TRADE_LIST);
        registerCreativeInvAction(ServerboundPackets1_19_4.CREATIVE_INVENTORY_ACTION, Type.FLAT_VAR_INT_ITEM);
        registerWindowPropertyEnchantmentHandler(ClientboundPackets1_19_4.WINDOW_PROPERTY);
        registerSpawnParticle1_19(ClientboundPackets1_19_4.SPAWN_PARTICLE);

        protocol.registerClientbound(ClientboundPackets1_19_4.OPEN_SIGN_EDITOR, wrapper -> {
            wrapper.passthrough(Type.POSITION1_14);
            wrapper.write(Type.BOOLEAN, true); // Front text
        });
        protocol.registerServerbound(ServerboundPackets1_19_4.UPDATE_SIGN, wrapper -> {
            wrapper.passthrough(Type.POSITION1_14);
            final boolean frontText = wrapper.read(Type.BOOLEAN);
            if (!frontText) {
                wrapper.cancel();
            }
        });

        final RecipeRewriter<ClientboundPackets1_19_4> recipeRewriter = new RecipeRewriter1_19_4<>(protocol);
        protocol.registerClientbound(ClientboundPackets1_19_4.DECLARE_RECIPES, wrapper -> {
            final int size = wrapper.passthrough(Type.VAR_INT);
            int newSize = size;
            for (int i = 0; i < size; i++) {
                final String type = wrapper.read(Type.STRING);
                final String cutType = Key.stripMinecraftNamespace(type);
                if (cutType.equals("smithing")) {
                    newSize--;
                    wrapper.read(Type.STRING); // Recipe identifier
                    wrapper.read(Type.FLAT_VAR_INT_ITEM_ARRAY_VAR_INT); // Base
                    wrapper.read(Type.FLAT_VAR_INT_ITEM_ARRAY_VAR_INT); // Additions
                    wrapper.read(Type.FLAT_VAR_INT_ITEM); // Result
                    continue;
                }

                wrapper.write(Type.STRING, type);
                wrapper.passthrough(Type.STRING); // Recipe Identifier
                recipeRewriter.handleRecipeType(wrapper, cutType);
            }

            wrapper.set(Type.VAR_INT, 0, newSize);
        });
    }

    private void handleBlockEntity(final BlockEntity blockEntity) {
        // Check for signs
        if (blockEntity.typeId() != 7 && blockEntity.typeId() != 8) {
            return;
        }

        final CompoundTag tag = blockEntity.tag();
        final CompoundTag frontText = new CompoundTag();
        tag.put("front_text", frontText);

        final ListTag messages = new ListTag(StringTag.class);
        for (int i = 1; i < 5; i++) {
            final Tag text = tag.get("Text" + i);
            messages.add(text != null ? text : new StringTag(ChatRewriter.emptyComponentString()));
        }
        frontText.put("messages", messages);

        final ListTag filteredMessages = new ListTag(StringTag.class);
        for (int i = 1; i < 5; i++) {
            final Tag text = tag.get("FilteredText" + i);
            filteredMessages.add(text != null ? text : new StringTag(ChatRewriter.emptyComponentString()));
        }
        frontText.put("filtered_messages", filteredMessages);

        final Tag color = tag.remove("Color");
        if (color != null) {
            frontText.put("color", color);
        }

        final Tag glowing = tag.remove("GlowingText");
        if (glowing != null) {
            frontText.put("has_glowing_text", glowing);
        }
    }
}