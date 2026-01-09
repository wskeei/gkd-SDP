package li.songe.gkd.sdp.ui

import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.ui.share.BaseViewModel

class SnapshotVm : BaseViewModel() {
    val snapshotsState = DbSet.snapshotDao.query().attachLoad()
        .stateInit(emptyList())
}