package polydungeons.item;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import polydungeons.entity.AnchorEntity;
import polydungeons.entity.PolyDungeonsEntities;
import polydungeons.network.PolyDungeonsServerNetwork;

import java.util.ArrayList;
import java.util.List;

public class AnchorItem extends Item {
    public AnchorItem() {
        super(new Settings().maxCount(16));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if(!world.isClient) {
            AnchorEntity newEntity = new AnchorEntity(PolyDungeonsEntities.ANCHOR, world);
            newEntity.setPos(user.getX(), user.getY() + 1.5, user.getZ());
            world.spawnEntity(newEntity);
            user.getStackInHand(hand).decrement(1);
        } else {
            //noinspection MethodCallSideOnly
            MinecraftClient.getInstance().gameRenderer.showFloatingItem(user.getStackInHand(hand));
        }
        return TypedActionResult.pass(user.getStackInHand(hand));
    }
}