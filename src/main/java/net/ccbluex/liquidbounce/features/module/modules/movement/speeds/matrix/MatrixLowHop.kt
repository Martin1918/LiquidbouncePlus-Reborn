package net.ccbluex.liquidbounce.features.module.modules.movement.speeds.matrix

import net.ccbluex.liquidbounce.event.MoveEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.speeds.SpeedMode
import net.ccbluex.liquidbounce.utils.MovementUtils

class MatrixLowHop : SpeedMode("MatrixLowHop") {

    override fun onMotion() {

    }
    override fun onUpdate() {
        if (mc.thePlayer.onGround && MovementUtils.isMoving()) {
            mc.thePlayer.jump()
            mc.thePlayer.motionY -= 0.116 * 0.03;
        }
    }
    override fun onMove(event: MoveEvent?) {}
}