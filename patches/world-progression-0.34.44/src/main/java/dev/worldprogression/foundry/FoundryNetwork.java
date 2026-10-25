package dev.worldprogression.foundry;

import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public final class FoundryNetwork {
 public record Guide(int entity,int page) implements CustomPayload {
  public static final Id<Guide>ID=new Id<>(FoundryContent.id("foundry_guide"));
  public static final PacketCodec<RegistryByteBuf,Guide>CODEC=PacketCodec.of((p,b)->{b.writeVarInt(p.entity);b.writeVarInt(p.page);},b->new Guide(b.readVarInt(),b.readVarInt()));
  public Id<? extends CustomPayload>getId(){return ID;}
 }
 public record Demo(int entity) implements CustomPayload {
  public static final Id<Demo>ID=new Id<>(FoundryContent.id("foundry_demo"));
  public static final PacketCodec<RegistryByteBuf,Demo>CODEC=PacketCodec.of((p,b)->b.writeVarInt(p.entity),b->new Demo(b.readVarInt()));
  public Id<? extends CustomPayload>getId(){return ID;}
 }
 public record Enter(int entity) implements CustomPayload {
  public static final Id<Enter>ID=new Id<>(FoundryContent.id("enter_gel_rift"));
  public static final PacketCodec<RegistryByteBuf,Enter>CODEC=PacketCodec.of((p,b)->b.writeVarInt(p.entity),b->new Enter(b.readVarInt()));
  public Id<? extends CustomPayload>getId(){return ID;}
 }
 public record Work(BlockPos pos,long start,int duration) implements CustomPayload {
  public static final Id<Work>ID=new Id<>(FoundryContent.id("foundry_work"));
  public static final PacketCodec<RegistryByteBuf,Work>CODEC=PacketCodec.of((p,b)->{b.writeBlockPos(p.pos);b.writeLong(p.start);b.writeVarInt(p.duration);},b->new Work(b.readBlockPos(),b.readLong(),b.readVarInt()));
  public Id<? extends CustomPayload>getId(){return ID;}
 }
 public record Speech(int entity,String text,int ticks) implements CustomPayload {
  public static final Id<Speech>ID=new Id<>(FoundryContent.id("foundry_speech"));
  public static final PacketCodec<RegistryByteBuf,Speech>CODEC=PacketCodec.of((p,b)->{b.writeVarInt(p.entity);b.writeString(p.text,512);b.writeVarInt(p.ticks);},b->new Speech(b.readVarInt(),b.readString(512),b.readVarInt()));
  public Id<? extends CustomPayload>getId(){return ID;}
 }
 public static void work(ServerWorld w,WorkstationEntity station,ServerPlayerEntity player){if(ServerPlayNetworking.canSend(player,Work.ID))ServerPlayNetworking.send(player,new Work(station.getPos(),station.started,station.duration));}
 public static void register(){PayloadTypeRegistry.playS2C().register(Guide.ID,Guide.CODEC);PayloadTypeRegistry.playC2S().register(Demo.ID,Demo.CODEC);ServerPlayNetworking.registerGlobalReceiver(Demo.ID,(packet,ctx)->ctx.server().execute(()->{var p=ctx.player();if(p.isAlive()&&!p.isSpectator()&&p.getWorld().getEntityById(packet.entity()) instanceof FoundryWorkerEntity worker&&worker.squaredDistanceTo(p)<=9)worker.showDemo(p);}));PayloadTypeRegistry.playC2S().register(Enter.ID,Enter.CODEC);PayloadTypeRegistry.playS2C().register(Work.ID,Work.CODEC);PayloadTypeRegistry.playS2C().register(Speech.ID,Speech.CODEC);ServerPlayNetworking.registerGlobalReceiver(Enter.ID,(packet,ctx)->ctx.server().execute(()->GelFoundry.request(ctx.player(),packet.entity())));}
 private FoundryNetwork(){}
}
