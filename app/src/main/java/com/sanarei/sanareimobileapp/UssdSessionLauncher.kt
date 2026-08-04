package com.sanarei.sanareimobileapp

import android.content.Context
import com.romellfudi.ussdlibrary.USSDController

/**
 * Starts the pinned USSD library after our own accessibility check.
 *
 * Version 1.4.a compares Android's abbreviated installed-service ID with its fully qualified
 * enabled-service ID. That incorrectly rejects a subclassed accessibility service on Samsung.
 */
object UssdSessionLauncher {
    fun start(
        context: Context,
        encodedCode: String,
        simSlot: Int,
        map: HashMap<String, List<String>>,
        callback: USSDController.CallbackInvoke
    ) {
        val controllerClass = USSDController::class.java
        val controller = USSDController

        controllerClass.getDeclaredField("context").apply {
            isAccessible = true
            set(controller, context)
        }
        controllerClass.getDeclaredField("map").apply {
            isAccessible = true
            set(controller, map)
        }
        controller.callbackInvoke = callback
        controllerClass.getDeclaredField("sendType").apply {
            isAccessible = true
            set(controller, false)
        }

        controllerClass.getDeclaredMethod(
            "dialUp",
            String::class.java,
            Int::class.javaPrimitiveType
        ).apply {
            isAccessible = true
            invoke(controller, encodedCode, simSlot)
        }
    }
}
