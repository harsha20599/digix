package com.littyl.digix

import android.app.Application
import com.littyl.digix.core.AssistantConfig
import com.littyl.digix.core.SosConfig
import com.littyl.digix.core.Store
import com.littyl.digix.engine.GoogleHome
import com.littyl.digix.engine.Notifications
import com.littyl.digix.engine.ReplyCallService

class DigixApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // The call interceptor reads codes synchronously, so the store must be ready first.
        Store.init(this)
        SosConfig.init(this)
        Notifications.createChannels(this)
        ReplyCallService.registerAccount(this)
        AssistantConfig.refresh(this)
        GoogleHome.init(this)
    }
}
