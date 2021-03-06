package polydungeons.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.TranslatableText;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import polydungeons.entity.ILivingEntity;
import polydungeons.entity.IServerWorld;
import polydungeons.entity.charms.AnchorEntity;
import polydungeons.entity.SplatEntity;
import polydungeons.entity.charms.CharmHelper;
import polydungeons.entity.charms.SubstituteEntity;

import java.util.List;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity implements ILivingEntity {

	@Shadow protected boolean dead;

	@Shadow public abstract float getMaxHealth();
	@Shadow public abstract float getHealth();
	@Shadow public abstract EntityAttributeInstance getAttributeInstance(EntityAttribute attribute);
	@Shadow public abstract void setHealth(float health);
	@Shadow public abstract boolean clearStatusEffects();

	@Unique private int timesTargeted = 1;
	@Unique private float targetPriority = 0;
	@Unique private boolean dirty = true;

	@Unique private PlayerEntity player;

	public LivingEntityMixin(EntityType<?> type, World world) {
		super(type, world);
	}

	@Inject(method = "baseTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;shouldDisplaySoulSpeedEffects()Z", ordinal = 0))
	private void onTick(CallbackInfo ci) {
		if (!world.isClient || isLogicalSideForUpdatingMovement()) {
			List<SplatEntity> splats = world.getEntities(SplatEntity.class, getBoundingBox(), EntityPredicates.VALID_ENTITY);
			EntityAttributeInstance movementSpeed = getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
			if (splats.isEmpty()) {
				if (movementSpeed != null) {
					movementSpeed.removeModifier(SplatEntity.SPLAT_SLOWDOWN_UUID);
				}
			} else {
				if (movementSpeed != null) {
					EntityAttributeModifier modifier = new EntityAttributeModifier(SplatEntity.SPLAT_SLOWDOWN_UUID, "Splat Slowdown", -0.5, EntityAttributeModifier.Operation.MULTIPLY_TOTAL);
					if (!movementSpeed.hasModifier(modifier)) {
						movementSpeed.addTemporaryModifier(modifier);
					}
				}
				if (splats.stream().anyMatch(SplatEntity::isFiery)) {
					setOnFireFor(2);
				}
			}
		}
	}

	@Inject(method = "tryUseTotem(Lnet/minecraft/entity/damage/DamageSource;)Z", at = @At("HEAD"), cancellable = true)
	private void tryUseTotem(DamageSource source, CallbackInfoReturnable<Boolean> info) {
		//noinspection ConstantConditions
		if((Object)this instanceof PlayerEntity && !world.isClient) {
			AnchorEntity anchor = CharmHelper.getClosestCharm((ServerWorld) world, ((IServerWorld) world).polydungeons_getAnchorPositions(), this, AnchorEntity.class);
			if (anchor != null) {
				requestTeleport(anchor.getX(), anchor.getY(), anchor.getZ());
				setHealth(1);
				clearStatusEffects();
				dead = false;
				anchor.kill();
				info.setReturnValue(true);
			}
		}
	}

	@ModifyVariable(method = "damage(Lnet/minecraft/entity/damage/DamageSource;F)Z", at = @At(value = "HEAD", target = "Lnet/minecraft/entity/LivingEntity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"))
	public float damage(float amount) {
		//noinspection ConstantConditions
		if((Object)this instanceof PlayerEntity && !world.isClient) {
			SubstituteEntity substitute = CharmHelper.getClosestCharm((ServerWorld) world, ((IServerWorld) world).polydungeons_getSubstitutePositions(), this, SubstituteEntity.class);
			if (substitute != null) {
				this.clearStatusEffects();
				substitute.setCapacity((int)(substitute.getCapacity() - amount));
				if (substitute.getCapacity() <= 0) {
					((PlayerEntity)(Object)this).sendMessage(new TranslatableText("item.polydungeons.substitute.broken"), true);
					substitute.kill();
				}
				return 0f;
			}
		}
		return amount;
	}

	private void recalculatePriority(PlayerEntity player, float maxHealth, float health, int timesTargeted, float distance) {
		this.targetPriority = maxHealth * (health / maxHealth) / timesTargeted / distance + maxHealth;
	}

	@Override
	public int getTimesTargeted() {
		return timesTargeted;
	}

	@Override
	public float getTargetPriority(PlayerEntity player) {
		if(this.player != player) {
			this.player = player;
			markDirty();
		}
		if(this.dirty) {
			recalculatePriority(player, this.getMaxHealth(), this.getMaxHealth() - this.getHealth() + 1, this.timesTargeted, this.distanceTo(player));
		}
		return this.targetPriority;
	}

	@Override
	public ILivingEntity setTimesHit(int timesHit) {
		this.timesTargeted = timesHit;
		this.markDirty();
		return this;
	}

	@Override
	public ILivingEntity incrementTimesTargeted() {
		this.timesTargeted++;
		this.markDirty();
		return this;
	}

	@Override
	public ILivingEntity markDirty() {
		this.dirty = true;
		return this;
	}
}
