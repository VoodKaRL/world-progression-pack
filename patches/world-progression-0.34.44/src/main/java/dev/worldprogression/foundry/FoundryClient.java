package dev.worldprogression.foundry;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.*;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/** E is contextual; every other use of the player's inventory binding is left to Minecraft. */
public final class FoundryClient {
 private static int aimed=-1,pressed=-1,pressTicks,ticks;
 private static BlockPos working;private static long began;private static int duration;
 private static ModelPart upper,lower,offUpper,offLower;private static boolean interactionHeld,modelLeft,modelSlim;
 public static int aimed(){return aimed;}
 public static float pressing(int id,float delta){return pressed==id&&pressTicks>0?(float)Math.sin(Math.clamp((7-pressTicks+delta)/7f,0,1)*Math.PI):0;}
 public static boolean key(long window,int key,int scan,int action){
  var c=MinecraftClient.getInstance();if(c.getWindow()!=null&&window==c.getWindow().getHandle()&&c.options.inventoryKey.matchesKey(key,scan)&&interactionHeld){if(action==GLFW.GLFW_RELEASE)interactionHeld=false;return true;}if(c.getWindow()==null||window!=c.getWindow().getHandle()||c.player==null||c.currentScreen!=null||dev.worldprogression.arrival.ArrivalClient.blocking()||!c.options.inventoryKey.matchesKey(key,scan)||aimed<0)return false;
  if(action==GLFW.GLFW_PRESS&&pressTicks==0&&ClientPlayNetworking.canSend(FoundryNetwork.Enter.ID)){interactionHeld=true;pressed=aimed;pressTicks=7;ClientPlayNetworking.send(new FoundryNetwork.Enter(aimed));}
  return true;
 }
 private static WorkstationEntity workingStation(){
  var c=MinecraftClient.getInstance();if(working==null||c.player==null||c.world==null||dev.worldprogression.arrival.ArrivalClient.blocking()||c.world.getTime()-began>=duration)return null;
  if(!(c.world.getBlockEntity(working) instanceof WorkstationEntity e)||!e.working()||e.demonstration||!c.player.getUuid().equals(e.artisan))return null;return e;
 }
 /** The working player stays in place for the full synced machine animation. */
 public static boolean locksMovement(){var c=MinecraftClient.getInstance();return workingStation()!=null&&c.currentScreen==null;}
 public static WorkstationEntity active(){var c=MinecraftClient.getInstance();if(c.player==null||c.currentScreen!=null||!c.options.getPerspective().isFirstPerson())return null;return workingStation();}
 public static boolean hands(){return active()!=null;}
 public static boolean cinematic(){return active()!=null;}
 public static void register(){
  EntityRendererRegistry.register(FoundryContent.RIFT,GelRiftRenderer::new);EntityRendererRegistry.register(FoundryContent.WORKER,FoundryWorkerRenderer::new);BlockEntityRendererFactories.register(FoundryContent.WORKSTATION,WorkstationRenderer::new);
  ClientPlayNetworking.registerGlobalReceiver(FoundryNetwork.Guide.ID,(p,ctx)->ctx.client().execute(()->ctx.client().setScreen(new dev.worldprogression.menu.WorkshopGuideScreen(null,p.entity(),p.page()))));
  ClientPlayNetworking.registerGlobalReceiver(FoundryNetwork.Work.ID,(p,ctx)->ctx.client().execute(()->{working=p.pos();began=p.start();duration=Math.clamp(p.duration(),0,400);}));
  ClientPlayNetworking.registerGlobalReceiver(FoundryNetwork.Speech.ID,(p,ctx)->ctx.client().execute(()->FoundrySpeechClient.receive(p)));
  ClientPlayConnectionEvents.DISCONNECT.register((h,c)->{aimed=pressed=-1;working=null;FoundrySpeechClient.clear();pressTicks=0;interactionHeld=false;});
  ClientTickEvents.END_CLIENT_TICK.register(c->{
   aimed=-1;if(pressTicks>0)pressTicks--;if(c.player==null||c.world==null||c.currentScreen!=null||c.options.hudHidden||c.player.hasVehicle()||dev.worldprogression.arrival.ArrivalClient.blocking())return;
   var eye=c.player.getEyePos();var end=eye.add(c.player.getRotationVec(1).multiply(5));double best=26;
   for(var e:c.world.getEntitiesByClass(GelRiftEntity.class,c.player.getBoundingBox().expand(6),GelRiftEntity::ready)){
    var hit=e.getBoundingBox().raycast(eye,end);if(hit.isEmpty()&&!e.getBoundingBox().contains(eye))continue;double d=hit.map(eye::squaredDistanceTo).orElse(0.0);
    var wall=c.world.raycast(new net.minecraft.world.RaycastContext(eye,end,net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,c.player));
    if(d<best&&(wall.getType()==net.minecraft.util.hit.HitResult.Type.MISS||eye.squaredDistanceTo(wall.getPos())+.05>=d)){best=d;aimed=e.getId();}
   }
  });
  WorldRenderEvents.AFTER_ENTITIES.register(ctx->{var station=active();if(station!=null&&ctx.matrixStack()!=null&&ctx.consumers()!=null)renderHands(station,ctx.matrixStack(),ctx.consumers(),ctx.camera().getPos(),ctx.tickCounter().getTickDelta(false));});
  HudRenderCallback.EVENT.register((context,counter)->{
   var c=MinecraftClient.getInstance();if(c.player==null||c.world==null||c.currentScreen!=null||c.options.hudHidden||dev.worldprogression.arrival.ArrivalClient.blocking()||cinematic())return;
   if(c.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit&&c.player.squaredDistanceTo(Vec3d.ofCenter(hit.getBlockPos()))<25&&WorkstationBlock.at(c.world,hit.getBlockPos())!=null){var e=WorkstationBlock.at(c.world,hit.getBlockPos());String text=e.kind().title+" · "+(e.demonstration?"Demostración":e.working()?"Trabajando…":!e.output.isEmpty()?"Clic: recoger":!e.input.isEmpty()?"Clic: trabajar · Agachado: retirar":"Mano vacía: abrir ranuras · Clic: colocar material");int x=(context.getScaledWindowWidth()-c.textRenderer.getWidth(text))/2,y=context.getScaledWindowHeight()-96;context.fill(x-5,y-3,x+c.textRenderer.getWidth(text)+5,y+11,0xB0122723);context.drawText(c.textRenderer,text,x,y,0xDCEBD0,true);}
  });
 }
 private static ModelPart arm(String name,int u,int v){var data=new ModelData();data.getRoot().addChild(name,ModelPartBuilder.create().uv(u,v).cuboid(modelSlim?-1.5f:-2,0,-2,modelSlim?3:4,6,4),ModelTransform.NONE);return TexturedModelData.of(data,64,64).createModel().getChild(name);}
 private static void segment(MatrixStack m,VertexConsumerProvider v,Vec3d camera,Vec3d a,Vec3d b,ModelPart model,Identifier skin,int light){var d=b.subtract(a);m.push();m.translate(a.x-camera.x,a.y-camera.y,a.z-camera.z);m.multiply(new Quaternionf().rotationTo(new Vector3f(0,1,0),d.toVector3f().normalize()));m.scale(.64f,(float)(d.length()/.375),.76f);model.render(m,v.getBuffer(RenderLayer.getEntityCutoutNoCull(skin)),light,OverlayTexture.DEFAULT_UV);m.pop();}
 private static Vec3d grip(WorkstationEntity e,Vec3d player,float age){
  var toward=Vec3d.ofBottomCenter(e.getPos()).subtract(player);toward=new Vec3d(toward.x,0,toward.z).normalize();boolean bench=e.kind()==FoundryContent.Station.DISMANTLE;float lift=WorkshopMotion.lift(age,bench);
  return Vec3d.ofBottomCenter(e.getPos()).subtract(toward.multiply(.34+.08*lift)).add(0,WorkshopMotion.contactHeight(bench)+.20+.08*lift,0);
 }
 private static void limb(MatrixStack m,VertexConsumerProvider v,Vec3d camera,Vec3d shoulder,Vec3d wrist,Vec3d right,ModelPart upper,ModelPart lower,Identifier skin,int light){
  var direction=wrist.subtract(shoulder);double length=direction.length();var outward=right.add(0,-.25,0);var normal=outward.subtract(direction.multiply(outward.dotProduct(direction)/Math.max(.001,length*length))).normalize();
  var elbow=shoulder.lerp(wrist,.5).add(normal.multiply(Math.sqrt(Math.max(.001,.38*.38-length*length*.25))));segment(m,v,camera,shoulder,elbow,upper,skin,light);segment(m,v,camera,elbow,wrist,lower,skin,light);
 }
 private static void renderHands(WorkstationEntity e,MatrixStack m,VertexConsumerProvider v,Vec3d camera,float delta){
  var c=MinecraftClient.getInstance();boolean left=c.player.getMainArm()==net.minecraft.util.Arm.LEFT,slim=c.player.getSkinTextures().model()==net.minecraft.client.util.SkinTextures.Model.SLIM;
  if(upper==null||modelLeft!=left||modelSlim!=slim){modelLeft=left;modelSlim=slim;upper=arm("upper",left?32:40,left?48:16);lower=arm("lower",left?32:40,left?54:22);offUpper=arm("offUpper",left?40:32,left?16:48);offLower=arm("offLower",left?40:32,left?22:54);}float sign=left?-1:1;
  float age=c.world.getTime()-e.started+delta,p=e.progress(delta);var forward=c.player.getRotationVec(delta);var right=new Vec3d(-Math.cos(Math.toRadians(c.player.getYaw())),0,-Math.sin(Math.toRadians(c.player.getYaw())));
  var shoulder=camera.add(right.multiply(.35*sign)).add(0,-.26,0).add(forward.multiply(.12));boolean strikes=e.kind()==FoundryContent.Station.ANVIL||e.kind()==FoundryContent.Station.DISMANTLE;
  var rest=camera.add(forward.multiply(.55)).add(right.multiply(.28*sign)).add(0,-.47,0);
  var toward=Vec3d.ofBottomCenter(e.getPos()).subtract(c.player.getPos());toward=new Vec3d(toward.x,0,toward.z).normalize();
  var desired=strikes?grip(e,c.player.getPos(),age).add(right.multiply(.14*sign)):rest.lerp(Vec3d.ofBottomCenter(e.getPos()).add(0,.95,0),Math.sin(p*Math.PI)*.65);
  var reach=desired.subtract(shoulder);var wrist=shoulder.add(reach.length()>.75?reach.normalize().multiply(.75):reach);int light=WorldRenderer.getLightmapCoordinates(c.world,e.getPos().up());var skin=c.player.getSkinTextures().texture();limb(m,v,camera,shoulder,wrist,right.multiply(sign),upper,lower,skin,light);
  float yaw=(float)Math.toDegrees(Math.atan2(toward.x,toward.z));
  m.push();m.translate(wrist.x-camera.x,wrist.y-camera.y,wrist.z-camera.z);m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
  if(strikes){m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(WorkshopMotion.angle(age,e.kind()==FoundryContent.Station.DISMANTLE)));m.translate(0,-.10,0);FoundryGeometry.hammer(m,v,light);}
  else if(e.kind()==FoundryContent.Station.QUENCH||e.kind()==FoundryContent.Station.FURNACE)FoundryGeometry.tongs(m,v,light);m.pop();
  if(strikes){
   var secondary=camera.add(right.multiply(-.34*sign)).add(0,-.28,0).add(forward.multiply(.12));var target=Vec3d.ofBottomCenter(e.getPos()).subtract(toward.multiply(.23)).add(right.multiply(-.23*sign)).add(0,WorkshopMotion.contactHeight(e.kind()==FoundryContent.Station.DISMANTLE)+.14,0);
   var d=target.subtract(secondary);var held=secondary.add(d.length()>.74?d.normalize().multiply(.74):d);limb(m,v,camera,secondary,held,right.multiply(-sign),offUpper,offLower,skin,light);
   m.push();m.translate(held.x-camera.x,held.y-camera.y,held.z-camera.z);m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));FoundryGeometry.tongs(m,v,light);m.pop();
  }
 }
 private FoundryClient(){}
}
