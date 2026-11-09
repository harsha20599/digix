package com.littyl.digix.engine

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.littyl.digix.R
import com.littyl.digix.core.Store

/** Lets people switch Digix on or off from Quick Settings. */
class MasterTileService : TileService() {

    override fun onStartListening() = refresh()

    override fun onClick() {
        Store.setEnabled(!Store.enabled.value)
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val on = Store.enabled.value
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = getString(if (on) R.string.home_state_on else R.string.home_state_off)
        tile.updateTile()
    }
}
