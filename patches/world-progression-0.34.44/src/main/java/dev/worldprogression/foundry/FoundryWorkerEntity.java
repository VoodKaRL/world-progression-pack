package dev.worldprogression.foundry;

import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.data.*;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Protected, persistent artisans. Demonstrations use visual samples and never generate loot. */
public final class FoundryWorkerEntity extends PathAwareEntity {
 private static final TrackedData<Integer>JOB=DataTracker.registerData(FoundryWorkerEntity.class,TrackedDataHandlerRegistry.INTEGER);
 private BlockPos home=GelFoundry.ARRIVAL;private long nextHint;private int route;
 public FoundryWorkerEntity(EntityType<? extends PathAwareEntity> type,World w){super(type,w);setPersistent();setInvulnerable(true);}
 protected void initDataTracker(DataTracker.Builder b){super.initDataTracker(b);b.add(JOB,0);}
 public int job(){return dataTracker.get(JOB);}public BlockPos home(){return home;}
 public void configure(int job,BlockPos home){dataTracker.set(JOB,Math.clamp(job,0,7));this.home=home.toImmutable();setCustomName(Text.literal(job==0?"Desmontador slime":job==1||job==2?"Herrero slime":job==3?"Templador slime":job==4?"Tejedora slime":job==5?"Prensador slime":"Aprendiz slime"));setCustomNameVisible(false);}
 protected void initGoals(){goalSelector.add(0,new SwimGoal(this));goalSelector.add(5,new LookAtEntityGoal(this,PlayerEntity.class,6));goalSelector.add(6,new LookAroundGoal(this));}
 public void setTarget(LivingEntity target){super.setTarget(null);}
 public boolean tryAttack(Entity e){return false;}
 public boolean damage(net.minecraft.entity.damage.DamageSource s,float amount){return false;}
 public boolean cannotDespawn(){return true;}public boolean canImmediatelyDespawn(double distance){return false;}
 public ActionResult interactMob(PlayerEntity player,Hand hand){
  if(getWorld().isClient)return ActionResult.SUCCESS;
  if(hand!=Hand.MAIN_HAND||!(player instanceof net.minecraft.server.network.ServerPlayerEntity p)||getWorld().getTime()<nextHint)return ActionResult.SUCCESS;
  nextHint=getWorld().getTime()+20;int job=job();
  FoundrySpeech.say(p,this,"Mira los materiales en mi guía. Abre las ranuras de la estación con la mano vacía; puedes pedirme una demostración.");
  int page=job==4?1:job==1||job==3?2:job==5?3:job==2?4:0;
  if(net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p,FoundryNetwork.Guide.ID))net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,new FoundryNetwork.Guide(getId(),page));
  return ActionResult.SUCCESS;
 }
 private long lastDemo=-100;
 public void showDemo(net.minecraft.server.network.ServerPlayerEntity p){
  if(p.squaredDistanceTo(this)>9||!p.isAlive()||p.isSpectator()||getWorld().getTime()-lastDemo<20)return;
  lastDemo=getWorld().getTime();
  if(job()>=6){FoundrySpeech.say(p,this,"Los artesanos de cada estación te muestran su trabajo. Yo llevo los materiales.");return;}
  if(getWorld().getBlockEntity(home) instanceof WorkstationEntity station&&!station.demonstrate(p))FoundrySpeech.say(p,this,"La estación tiene materiales de un jugador. Retíralos o termina el trabajo antes de la demostración.");
 }

 public void tick(){super.tick();if(!(getWorld() instanceof ServerWorld w))return;setTarget(null);
  if(age%20==0){
   if(job()>=6){int[][] points={{-6,3},{-6,-2},{7,-2},{7,8}};var p=points[(route+job())%4];var at=new Vec3d(p[0]+.5,65,p[1]+.5);if(squaredDistanceTo(at)<2)route++;else if(getNavigation().isIdle())getNavigation().startMovingTo(at.x,at.y,at.z,.65);}
   else if(squaredDistanceTo(new Vec3d(home.getX()+1.65,home.getY(),home.getZ()+.5))>.12)getNavigation().startMovingTo(home.getX()+1.65,home.getY(),home.getZ()+.5,.7);
   if(job()<6){getLookControl().lookAt(home.getX()+.5,home.getY()+.8,home.getZ()+.5);if(w.getBlockEntity(home) instanceof WorkstationEntity station&&station.demonstration&&station.working())swingHand(Hand.MAIN_HAND);}
  }
  if(getY()<63||Math.abs(getX())>27||Math.abs(getZ())>21){refreshPositionAndAngles(home.getX()+1.65,65,home.getZ()+.5,90,0);getNavigation().stop();}
 }
 public void writeCustomDataToNbt(NbtCompound n){super.writeCustomDataToNbt(n);n.putInt("FoundryJob",job());n.putLong("FoundryHome",home.asLong());n.putInt("Route",route);}
 public void readCustomDataFromNbt(NbtCompound n){super.readCustomDataFromNbt(n);configure(n.getInt("FoundryJob"),BlockPos.fromLong(n.getLong("FoundryHome")));route=n.getInt("Route");}
}
