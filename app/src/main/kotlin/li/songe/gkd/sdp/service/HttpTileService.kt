package li.songe.gkd.sdp.service

class HttpTileService : BaseTileService() {
    override val activeFlow = HttpService.isRunning

    init {
        onTileClicked {
            if (HttpService.isRunning.value) {
                HttpService.stop()
            } else {
                HttpService.start()
            }
        }
    }
}