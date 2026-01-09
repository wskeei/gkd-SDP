package li.songe.gkd.sdp.service

import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.store.switchStoreEnableMatch
import li.songe.gkd.sdp.util.mapState

class MatchTileService : BaseTileService() {
    override val activeFlow = storeFlow.mapState(scope) { it.enableMatch }

    init {
        onTileClicked { switchStoreEnableMatch() }
    }
}