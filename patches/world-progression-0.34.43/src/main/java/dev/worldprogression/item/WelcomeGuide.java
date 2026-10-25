package dev.worldprogression.item;
import java.util.*;
import net.minecraft.item.*;import net.minecraft.util.*;import net.minecraft.entity.player.PlayerEntity;import net.minecraft.world.*;import net.minecraft.nbt.*;import net.minecraft.registry.*;import net.minecraft.server.MinecraftServer;import net.minecraft.server.network.ServerPlayerEntity;
import net.fabricmc.fabric.api.networking.v1.*;import net.minecraft.network.packet.CustomPayload;import net.minecraft.network.codec.PacketCodec;import net.minecraft.network.RegistryByteBuf;
/** Persistent starter items; the cinematic only selects an existing guide. */
public final class WelcomeGuide {
 public record Open() implements CustomPayload {public static final Id<Open> ID=new Id<>(Identifier.of("worldprogression:open_info"));public static final PacketCodec<RegistryByteBuf,Open> CODEC=PacketCodec.unit(new Open());public Id<? extends CustomPayload> getId(){return ID;}}
 public record Intro(boolean bag) implements CustomPayload {public static final Id<Intro> ID=new Id<>(Identifier.of("worldprogression:arrival_intro"));public static final PacketCodec<RegistryByteBuf,Intro> CODEC=PacketCodec.of((p,b)->b.writeBoolean(p.bag()),b->new Intro(b.readBoolean()));public Id<? extends CustomPayload> getId(){return ID;}}
 public record NoIntro() implements CustomPayload {public static final Id<NoIntro> ID=new Id<>(Identifier.of("worldprogression:arrival_no_intro"));public static final PacketCodec<RegistryByteBuf,NoIntro> CODEC=PacketCodec.unit(new NoIntro());public Id<? extends CustomPayload> getId(){return ID;}}
 public record Select() implements CustomPayload {public static final Id<Select> ID=new Id<>(Identifier.of("worldprogression:select_guide"));public static final PacketCodec<RegistryByteBuf,Select> CODEC=PacketCodec.unit(new Select());public Id<? extends CustomPayload> getId(){return ID;}}
 public static Item ITEM;
 public static final class Given extends PersistentState {
  public final Set<UUID> players=new HashSet<>(),bags=new HashSet<>(),arrivalIntros=new HashSet<>();
  static Given read(NbtCompound n){var s=new Given();for(var key:n.getKeys())try{if(n.getBoolean(key))s.players.add(UUID.fromString(key));}catch(IllegalArgumentException ignored){}for(var value:n.getList("starter_bags",NbtElement.STRING_TYPE))try{s.bags.add(UUID.fromString(value.asString()));}catch(IllegalArgumentException ignored){}if(n.contains("arrival_intros",NbtElement.LIST_TYPE)){for(var value:n.getList("arrival_intros",NbtElement.STRING_TYPE))try{s.arrivalIntros.add(UUID.fromString(value.asString()));}catch(IllegalArgumentException ignored){}}else{s.arrivalIntros.addAll(s.players);s.markDirty();}return s;}
  public NbtCompound writeNbt(NbtCompound n,RegistryWrapper.WrapperLookup lookup){for(var id:players)n.putBoolean(id.toString(),true);var list=new NbtList();bags.forEach(id->list.add(NbtString.of(id.toString())));n.put("starter_bags",list);var intros=new NbtList();arrivalIntros.forEach(id->intros.add(NbtString.of(id.toString())));n.put("arrival_intros",intros);return n;}
 }
 private static final class Joining {int ticks;boolean introduced;}
 private static final Map<MinecraftServer,Map<UUID,Joining>> pending=new WeakHashMap<>();
 public static Given state(MinecraftServer server){return server.getOverworld().getPersistentStateManager().getOrCreate(new PersistentState.Type<>(Given::new,(n,r)->Given.read(n),null),"verdania-welcome-guides");}
 public static int find(ServerPlayerEntity p,Item item){for(int i=0;i<p.getInventory().size();i++)if(p.getInventory().getStack(i).isOf(item))return i;return -1;}
 public static boolean give(ServerPlayerEntity player){
  var s=state(player.getServer());if(s.players.contains(player.getUuid()))return false;
  if(find(player,ITEM)<0&&!player.getInventory().insertStack(new ItemStack(ITEM)))return false;
  s.players.add(player.getUuid());s.markDirty();player.currentScreenHandler.sendContentUpdates();return true;
 }
 public static boolean giveBag(ServerPlayerEntity player){
  var s=state(player.getServer());if(s.bags.contains(player.getUuid()))return false;var bag=dev.worldprogression.bag.ProgressionBags.ITEMS.getFirst();
  if(find(player,bag)<0&&!player.getInventory().insertStack(new ItemStack(bag)))return false;
  s.bags.add(player.getUuid());s.markDirty();player.currentScreenHandler.sendContentUpdates();return true;
 }
 public static boolean select(ServerPlayerEntity player){
  if(player.currentScreenHandler!=player.playerScreenHandler)return false;int at=find(player,ITEM);if(at<0)return false;
  var inv=player.getInventory();int hotbar=at<9?at:inv.selectedSlot;
  if(at>=9){for(int i=0;i<9;i++)if(inv.getStack(i).isEmpty()){hotbar=i;break;}var old=inv.getStack(hotbar);inv.setStack(hotbar,inv.getStack(at));inv.setStack(at,old);}
  inv.selectedSlot=hotbar;inv.markDirty();player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket(hotbar));player.playerScreenHandler.sendContentUpdates();return true;
 }
 private static void tick(MinecraftServer server){
  var entries=pending.get(server);if(entries==null)return;
  var iterator=entries.entrySet().iterator();while(iterator.hasNext()){
   var e=iterator.next();var p=server.getPlayerManager().getPlayer(e.getKey());if(p==null){iterator.remove();continue;}var joining=e.getValue();int t=++joining.ticks;
   if(t%20==0){give(p);giveBag(p);}
   var s=state(server);
   if(!joining.introduced&&!s.arrivalIntros.contains(p.getUuid())&&ServerPlayNetworking.canSend(p,Intro.ID)){
    ServerPlayNetworking.send(p,new Intro(find(p,dev.worldprogression.bag.ProgressionBags.ITEMS.getFirst())>=0));s.arrivalIntros.add(p.getUuid());s.markDirty();joining.introduced=true;
   }
   if(t>=20&&(joining.introduced||t>=200)&&s.players.contains(p.getUuid())&&s.bags.contains(p.getUuid()))iterator.remove();
  }
 }
 public static void register(){
  PayloadTypeRegistry.playS2C().register(Open.ID,Open.CODEC);PayloadTypeRegistry.playS2C().register(Intro.ID,Intro.CODEC);PayloadTypeRegistry.playS2C().register(NoIntro.ID,NoIntro.CODEC);PayloadTypeRegistry.playC2S().register(Select.ID,Select.CODEC);
  ITEM=Registry.register(Registries.ITEM,Identifier.of("worldprogression:verdania_guide"),new Item(new Item.Settings().maxCount(1)){
   public TypedActionResult<ItemStack> use(World world,PlayerEntity player,Hand hand){if(player instanceof ServerPlayerEntity p&&ServerPlayNetworking.canSend(p,Open.ID))ServerPlayNetworking.send(p,new Open());return TypedActionResult.success(player.getStackInHand(hand),world.isClient);}
  });
  ServerPlayNetworking.registerGlobalReceiver(Select.ID,(payload,context)->context.server().execute(()->select(context.player())));
  ServerPlayConnectionEvents.JOIN.register((h,sender,server)->{give(h.player);giveBag(h.player);var joining=new Joining();joining.introduced=state(server).arrivalIntros.contains(h.player.getUuid());if(joining.introduced&&ServerPlayNetworking.canSend(h.player,NoIntro.ID))ServerPlayNetworking.send(h.player,new NoIntro());pending.computeIfAbsent(server,s->new HashMap<>()).put(h.player.getUuid(),joining);});
  net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(WelcomeGuide::tick);
  net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(pending::remove);
 }
 private WelcomeGuide(){}
}
