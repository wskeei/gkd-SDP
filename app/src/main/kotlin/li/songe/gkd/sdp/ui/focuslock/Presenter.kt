@file:JvmName("FocusLockPresenter0")

package li.songe.gkd.sdp.ui

fun FocusLockUiState.reduce(action: FocusLockAction): FocusLockUiState = when (action) {
    is FocusLockAction.ToggleExpandSubs -> copy(
        expandedSubs = if (expandedSubs.contains(action.subsId)) {
            expandedSubs - action.subsId
        } else {
            expandedSubs + action.subsId
        },
    )
    is FocusLockAction.ToggleExpandApp -> copy(
        expandedApps = if (expandedApps.contains(action.key)) {
            expandedApps - action.key
        } else {
            expandedApps + action.key
        },
    )
    is FocusLockAction.ToggleRuleSelection -> this
}

fun FocusLockVm.present(
    subStates: List<SubscriptionState>,
    expandedSubs: Set<Long>,
    expandedApps: Set<String>,
): FocusLockUiState = FocusLockUiState(
    subStates = subStates,
    expandedSubs = expandedSubs,
    expandedApps = expandedApps,
)
