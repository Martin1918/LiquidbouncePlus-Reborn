package net.ccbluex.liquidbounce.features.module.modules.world

import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.ModuleCategory
import net.ccbluex.liquidbounce.features.module.ModuleInfo
import net.ccbluex.liquidbounce.features.module.modules.combat.KillAura
import net.ccbluex.liquidbounce.features.module.modules.movement.Fly
import net.ccbluex.liquidbounce.features.module.modules.movement.Speed
import net.ccbluex.liquidbounce.features.module.modules.movement.TargetStrafe
import net.ccbluex.liquidbounce.utils.*
import net.ccbluex.liquidbounce.utils.timer.MSTimer
import net.ccbluex.liquidbounce.value.BoolValue
import net.ccbluex.liquidbounce.value.IntegerValue
import net.ccbluex.liquidbounce.value.ListValue
import net.minecraft.network.Packet
import net.minecraft.network.play.client.*
import net.minecraft.network.play.client.C03PacketPlayer.*
import net.minecraft.network.play.server.S08PacketPlayerPosLook
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import kotlin.random.Random

@ModuleInfo(name = "Disabler", description = "Disable some anticheats' checks.", category = ModuleCategory.WORLD)
class Disabler : Module() {


	private val ConfirmTransaction = BoolValue ("ConfirmTransaction", false)
	private val PacketClientStatus = BoolValue ("PacketClientStatus", false)
	private val PacketKeepAlive = BoolValue ("PacketKeepAlive", false)
	private val PacketSpectate = BoolValue ("PacketSpectate", false)
	private val PacketInput = BoolValue ("PacketInput", false)
	private val PosCancel = BoolValue ("PosCancel", false)
	private val NoPayload = BoolValue ("NoPayload", false)



	// debug
	private val debugValue = BoolValue("Debug", false)

	val speed = LiquidBounce.moduleManager.getModule(Speed::class.java)!!

	fun isMoving(): Boolean = (mc.thePlayer != null && (mc.thePlayer.movementInput.moveForward != 0F || mc.thePlayer.movementInput.moveStrafe != 0F || mc.thePlayer.movementInput.sneak || mc.thePlayer.movementInput.jump))

	fun debug(s: String, force: Boolean = false) {
		if (debugValue.get() || force)
			ClientUtils.displayChatMessage("§7[§3§lDisabler§7]§f $s")
	}

	override fun onDisable() {

		mc.thePlayer.motionY = 0.0
		MovementUtils.strafe(0F)
		mc.timer.timerSpeed = 1F


	}


	@EventTarget
	fun onPacket(event: PacketEvent) {
		val packet = event.packet

		if(PacketInput.get()){
			PacketUtils.sendPacketNoEvent(C0CPacketInput(0.98F, 0.0F, false, false))
		}

		if(ConfirmTransaction.get()){
			if(packet is C0FPacketConfirmTransaction)
				event.cancelEvent()
		}

		if(PacketKeepAlive.get()){
			if(packet is C00PacketKeepAlive)
				event.cancelEvent()
		}

		if(PacketClientStatus.get()){
			PacketUtils.sendPacketNoEvent(C16PacketClientStatus(C16PacketClientStatus.EnumState.PERFORM_RESPAWN))
		}

		if(PacketSpectate.get()){
			PacketUtils.sendPacketNoEvent(C18PacketSpectate(mc.thePlayer.uniqueID))
		}

		if(PosCancel.get()){
			if(packet is C03PacketPlayer){
				if(mc.thePlayer.ticksExisted % 3 != 0){
					event.cancelEvent()
				}
			}
		}

		if(NoPayload.get()){
			if(packet is C17PacketCustomPayload)
				event.cancelEvent()
		}


	}

	/*@EventTarget(priority = 2)
	fun onMotion(event: MotionEvent) {
		val killAura = LiquidBounce.moduleManager.getModule(KillAura::class.java)!! as KillAura
		val fly = LiquidBounce.moduleManager.getModule(Fly::class.java)!! as Fly
		val targetStrafe = LiquidBounce.moduleManager.getModule(TargetStrafe::class.java)!! as TargetStrafe


	}

	@EventTarget
	fun onUpdate(event: UpdateEvent) {

	}

	@EventTarget
	fun onWorld(event: WorldEvent) {

	}*/

}