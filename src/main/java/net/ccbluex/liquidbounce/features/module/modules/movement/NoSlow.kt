/*
 * LiquidBounce+ Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/WYSI-Foundation/LiquidBouncePlus/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement

import de.gerrygames.viarewind.utils.PacketUtil
import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.ModuleCategory
import net.ccbluex.liquidbounce.features.module.ModuleInfo
import net.ccbluex.liquidbounce.features.module.modules.combat.KillAura
import net.ccbluex.liquidbounce.utils.*
import net.ccbluex.liquidbounce.utils.extensions.rayTraceCustom
import net.ccbluex.liquidbounce.utils.timer.MSTimer
import net.ccbluex.liquidbounce.value.BoolValue
import net.ccbluex.liquidbounce.value.FloatValue
import net.ccbluex.liquidbounce.value.IntegerValue
import net.ccbluex.liquidbounce.value.ListValue
import net.minecraft.item.*
import net.minecraft.network.Packet
import net.minecraft.network.play.INetHandlerPlayServer
import net.minecraft.network.play.client.*
import net.minecraft.network.play.client.C03PacketPlayer.*
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minecraft.util.MovingObjectPosition


@ModuleInfo(name = "NoSlow", spacedName = "No Slow", category = ModuleCategory.MOVEMENT, description = "Prevent you from getting slowed down by items (swords, foods, etc.) and liquids.")
class NoSlow : Module() {
    private val msTimer = MSTimer()
    private val modeValue = ListValue("PacketMode", arrayOf("Vanilla", "Blink", "Intave", "NCP", "AAC", "AAC5", "Custom","OldIntave","Watchdog","SwitchItem"), "Vanilla")
    private val blockForwardMultiplier = FloatValue("BlockForwardMultiplier", 1.0F, 0.2F, 1.0F, "x")
    private val blockStrafeMultiplier = FloatValue("BlockStrafeMultiplier", 1.0F, 0.2F, 1.0F, "x")
    private val consumeForwardMultiplier = FloatValue("ConsumeForwardMultiplier", 1.0F, 0.2F, 1.0F, "x")
    private val consumeStrafeMultiplier = FloatValue("ConsumeStrafeMultiplier", 1.0F, 0.2F, 1.0F, "x")
    private val bowForwardMultiplier = FloatValue("BowForwardMultiplier", 1.0F, 0.2F, 1.0F, "x")
    private val bowStrafeMultiplier = FloatValue("BowStrafeMultiplier", 1.0F, 0.2F, 1.0F, "x")
    val sneakForwardMultiplier = FloatValue("SneakForwardMultiplier", 1.0F, 0.3F, 1.0F, "x")
    val sneakStrafeMultiplier = FloatValue("SneakStrafeMultiplier", 1.0F, 0.3F, 1.0F, "x")
    private val customRelease = BoolValue("CustomReleasePacket", false, { modeValue.get().equals("custom", true) })
    private val customPlace = BoolValue("CustomPlacePacket", false, { modeValue.get().equals("custom", true) })
    private val customOnGround = BoolValue("CustomOnGround", false, { modeValue.get().equals("custom", true) })
    private val customDelayValue = IntegerValue("CustomDelay", 60, 0, 1000, "ms", { modeValue.get().equals("custom", true) })
    private val ciucValue = BoolValue("CheckInUseCount", true, { modeValue.get().equals("blink", true) })
    private val packetTriggerValue = ListValue("PacketTrigger", arrayOf("PreRelease", "PostRelease"), "PostRelease", { modeValue.get().equals("blink", true) })
    private val debugValue = BoolValue("Debug", false, {modeValue.get().equals("blink", true) })

    // Soulsand
    val soulsandValue = BoolValue("Soulsand", true)
    val liquidPushValue = BoolValue("LiquidPush", true)
    private var released = true
    private val blinkPackets = mutableListOf<Packet<INetHandlerPlayServer>>()
    private var lastX = 0.0
    private var lastY = 0.0
    private var lastZ = 0.0
    private var lastOnGround = false

    private var fasterDelay = false
    private var placeDelay = 0L
    private val timer = MSTimer()

    override fun onEnable() {
        blinkPackets.clear()
        msTimer.reset()
    }

    override fun onDisable() {
        blinkPackets.forEach {
            PacketUtils.sendPacketNoEvent(it)
        }
        blinkPackets.clear()
    }

    override val tag: String?
        get() = modeValue.get()

    private fun sendPacket(event : MotionEvent, sendC07 : Boolean, sendC08 : Boolean, delay : Boolean, delayValue : Long, onGround : Boolean, watchDog : Boolean = false) {
        val digging = C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos(-1,-1,-1), EnumFacing.DOWN)
        val blockPlace = C08PacketPlayerBlockPlacement(mc.thePlayer.inventory.getCurrentItem())
        val blockMent = C08PacketPlayerBlockPlacement(BlockPos(-1, -1, -1), 255, mc.thePlayer.inventory.getCurrentItem(), 0f, 0f, 0f)
        if(onGround && !mc.thePlayer.onGround) {
            return
        }
        if(sendC07 && event.eventState == EventState.PRE) {
            if(delay && msTimer.hasTimePassed(delayValue)) {
                mc.netHandler.addToSendQueue(digging)
            } else if(!delay) {
                mc.netHandler.addToSendQueue(digging)
            }
        }
        if(sendC08 && event.eventState == EventState.POST) {
            if(delay && msTimer.hasTimePassed(delayValue) && !watchDog) {
                mc.netHandler.addToSendQueue(blockPlace)
                msTimer.reset()
            } else if(!delay && !watchDog) {
                mc.netHandler.addToSendQueue(blockPlace)
            } else if(watchDog) {
                mc.netHandler.addToSendQueue(blockMent)
            }
        }
    }

    @EventTarget
    fun onPacket(event: PacketEvent) {
        val packet = event.packet
        val killAura = LiquidBounce.moduleManager[KillAura::class.java]!! as KillAura

        if (modeValue.get().equals(
                "blink",
                true
            ) && !(killAura.state && killAura.blockingStatus) && mc.thePlayer.itemInUse != null && mc.thePlayer.itemInUse.item != null
        ) {
            val item = mc.thePlayer.itemInUse.item
            if (mc.thePlayer.isUsingItem && (item is ItemFood || item is ItemBucketMilk || item is ItemPotion) && (!ciucValue.get() || mc.thePlayer.itemInUseCount >= 1)) {
                if (packet is C04PacketPlayerPosition || packet is C06PacketPlayerPosLook) {
                    if (mc.thePlayer.positionUpdateTicks >= 20 && packetTriggerValue.get()
                            .equals("postrelease", true)
                    ) {
                        (packet as C03PacketPlayer).x = lastX
                        (packet as C03PacketPlayer).y = lastY
                        (packet as C03PacketPlayer).z = lastZ
                        (packet as C03PacketPlayer).onGround = lastOnGround
                        if (debugValue.get())
                            ClientUtils.displayChatMessage("pos update reached 20")
                    } else {
                        event.cancelEvent()
                        if (packetTriggerValue.get().equals("postrelease", true))
                            PacketUtils.sendPacketNoEvent(C03PacketPlayer(lastOnGround))
                        blinkPackets.add(packet as Packet<INetHandlerPlayServer>)
                        if (debugValue.get())
                            ClientUtils.displayChatMessage("packet player (movement) added at ${blinkPackets.size - 1}")
                    }
                } else if (packet is C05PacketPlayerLook) {
                    event.cancelEvent()
                    if (packetTriggerValue.get().equals("postrelease", true))
                        PacketUtils.sendPacketNoEvent(C03PacketPlayer(lastOnGround))
                    blinkPackets.add(packet as Packet<INetHandlerPlayServer>)
                    if (debugValue.get())
                        ClientUtils.displayChatMessage("packet player (rotation) added at ${blinkPackets.size - 1}")
                } else if (packet is C03PacketPlayer) {
                    if (packetTriggerValue.get().equals("prerelease", true) || packet.onGround != lastOnGround) {
                        event.cancelEvent()
                        blinkPackets.add(packet as Packet<INetHandlerPlayServer>)
                        if (debugValue.get())
                            ClientUtils.displayChatMessage("packet player (idle) added at ${blinkPackets.size - 1}")
                    }
                }
                if (packet is C0BPacketEntityAction) {
                    event.cancelEvent()
                    blinkPackets.add(packet as Packet<INetHandlerPlayServer>)
                    if (debugValue.get())
                        ClientUtils.displayChatMessage("packet action added at ${blinkPackets.size - 1}")
                }
                if (packet is C07PacketPlayerDigging && packetTriggerValue.get().equals("prerelease", true)) {
                    if (blinkPackets.size > 0) {
                        blinkPackets.forEach {
                            PacketUtils.sendPacketNoEvent(it)
                        }
                        if (debugValue.get())
                            ClientUtils.displayChatMessage("sent ${blinkPackets.size} packets.")
                        blinkPackets.clear()
                    }
                }
            }
        }
        if(modeValue.isMode("Watchdog")){
            if (packet is C08PacketPlayerBlockPlacement) {
                if (mc.gameSettings.keyBindUseItem.isKeyDown && mc.thePlayer.heldItem != null && (mc.thePlayer.heldItem.item is ItemFood || mc.thePlayer.heldItem.item is ItemBucketMilk || mc.thePlayer.heldItem.item is ItemPotion && !ItemPotion.isSplash(mc.thePlayer.heldItem.metadata) || mc.thePlayer.heldItem.item is ItemBow)) {
                    if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit === MovingObjectPosition.MovingObjectType.BLOCK && (packet as C08PacketPlayerBlockPlacement).position != BlockPos(-1, -1, 1)) return
                    event.cancelEvent()
                    val position: MovingObjectPosition = mc.thePlayer.rayTraceCustom(
                        mc.playerController.blockReachDistance.toDouble(), mc.thePlayer.rotationYaw, 90f)
                        ?: return
                    val rot = Rotation(mc.thePlayer.rotationYaw, 90f)
                    RotationUtils.setTargetRotation(rot)
                    sendUseItem(position)
                }
            }
        }
    }

    private fun sendUseItem(mouse: MovingObjectPosition) {
        val facingX = (mouse.hitVec.xCoord - mouse.blockPos.x.toDouble()).toFloat()
        val facingY = (mouse.hitVec.yCoord - mouse.blockPos.y.toDouble()).toFloat()
        val facingZ = (mouse.hitVec.zCoord - mouse.blockPos.z.toDouble()).toFloat()
        PacketUtils.sendPacketNoEvent(
            C08PacketPlayerBlockPlacement(
                mouse.blockPos,
                mouse.sideHit.index,
                mc.thePlayer.heldItem,
                facingX,
                facingY,
                facingZ
            )
        )
    }

    @EventTarget
    fun onMotion(event: MotionEvent) {
        if (!MovementUtils.isMoving() && !modeValue.get().equals("blink", true))
            return

        val heldItem = mc.thePlayer.heldItem
        val killAura = LiquidBounce.moduleManager[KillAura::class.java]!! as KillAura

        when (modeValue.get().toLowerCase()) {
            "aac5" -> if (event.eventState == EventState.POST && (mc.thePlayer.isUsingItem || mc.thePlayer.isBlocking || killAura.blockingStatus)) {
                mc.netHandler.addToSendQueue(
                    C08PacketPlayerBlockPlacement(
                        BlockPos(-1, -1, -1),
                        255,
                        mc.thePlayer.inventory.getCurrentItem(),
                        0f,
                        0f,
                        0f
                    )
                )
            }

            "blink" -> {
                if (event.eventState == EventState.PRE && !mc.thePlayer.isUsingItem && !mc.thePlayer.isBlocking) {
                    lastX = event.x
                    lastY = event.y
                    lastZ = event.z
                    lastOnGround = event.onGround
                    if (blinkPackets.size > 0 && packetTriggerValue.get().equals("postrelease", true)) {
                        blinkPackets.forEach {
                            PacketUtils.sendPacketNoEvent(it)
                        }
                        if (debugValue.get())
                            ClientUtils.displayChatMessage("sent ${blinkPackets.size} packets.")
                        blinkPackets.clear()
                    }
                }
            }

            "intave" -> {
                if ((mc.thePlayer.isUsingItem || mc.thePlayer.isBlocking) && timer.hasTimePassed(placeDelay)) {
                    mc.playerController.syncCurrentPlayItem()
                    mc.netHandler.addToSendQueue(
                        C07PacketPlayerDigging(
                            C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                            BlockPos.ORIGIN,
                            EnumFacing.DOWN
                        )
                    )
                    if (event.eventState == EventState.POST) {
                        placeDelay = 200L
                        if (fasterDelay) {
                            placeDelay = 100L
                            fasterDelay = false
                        } else
                            fasterDelay = true
                        timer.reset()
                    }
                }
            }

            "oldintave" -> {
                if (mc.thePlayer.isUsingItem) {
                    if (event.eventState == EventState.PRE) {
                        mc.netHandler.addToSendQueue(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem % 8 + 1))
                        mc.netHandler.addToSendQueue(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem))
                    }
                    if (event.eventState == EventState.POST) {
                        mc.netHandler.addToSendQueue(
                            C08PacketPlayerBlockPlacement(
                                mc.thePlayer.inventoryContainer.getSlot(
                                    mc.thePlayer.inventory.currentItem + 36
                                ).stack
                            )
                        )
                    }
                }
            }
            "watchdog" -> {
                if ((mc.thePlayer.isUsingItem || killAura.blockingStatus) && mc.thePlayer.heldItem != null && mc.thePlayer.heldItem.item is ItemSword) {
                    if (event.eventState == EventState.PRE){
                        mc.netHandler.addToSendQueue(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem % 8 + 1))
                        mc.netHandler.addToSendQueue(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem))
                    }
                    if(event.eventState == EventState.POST){
                        mc.netHandler.addToSendQueue(C08PacketPlayerBlockPlacement(mc.thePlayer.inventoryContainer.getSlot(mc.thePlayer.inventory.currentItem + 36).stack))
                    }
                }
            }
            "switchitem" -> {
                if (mc.thePlayer.isUsingItem || killAura.blockingStatus) {
                    mc.netHandler.addToSendQueue(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem % 8 + 1))
                    mc.netHandler.addToSendQueue(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem))
                }
            }
            else -> {
                if (!mc.thePlayer.isBlocking && !killAura.blockingStatus)
                    return
                when (modeValue.get().toLowerCase()) {
                    "aac" -> {
                        if (mc.thePlayer.ticksExisted % 3 == 0)
                            sendPacket(event, true, false, false, 0, false)
                        else
                            sendPacket(event, false, true, false, 0, false)
                    }
                    "ncp" -> sendPacket(event, true, true, false, 0, false)
                    "custom" -> sendPacket(event, customRelease.get(), customPlace.get(), customDelayValue.get() > 0, customDelayValue.get().toLong(), customOnGround.get())
                }
            }
        }
    }

    @EventTarget
    fun onSlowDown(event: SlowDownEvent) {
        val heldItem = mc.thePlayer.heldItem?.item

        event.forward = getMultiplier(heldItem, true)
        event.strafe = getMultiplier(heldItem, false)
    }

    private fun getMultiplier(item: Item?, isForward: Boolean) = when (item) {
        is ItemFood, is ItemPotion, is ItemBucketMilk -> {
            if (isForward) this.consumeForwardMultiplier.get() else this.consumeStrafeMultiplier.get()
        }
        is ItemSword -> {
            if (isForward) this.blockForwardMultiplier.get() else this.blockStrafeMultiplier.get()
        }
        is ItemBow -> {
            if (isForward) this.bowForwardMultiplier.get() else this.bowStrafeMultiplier.get()
        }
        else -> 0.2F
    }
}
