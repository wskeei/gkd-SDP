package li.songe.gkd.sdp.service

class ActivityTileService : BaseTileService() {
    override val activeFlow = ActivityService.isRunning

    init {
        onTileClicked {
            if (ActivityService.isRunning.value) {
                ActivityService.stop()
            } else {
                ActivityService.start()
            }
        }
    }
}
