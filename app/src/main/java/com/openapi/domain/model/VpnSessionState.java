package com.openapi.domain.model;

import org.strongswan.android.logic.VpnStateService;
import org.strongswan.android.logic.imc.ImcState;

/**
 * VPN 会话的唯一状态快照。
 * <p>
 * 不可变：状态变化时整体替换，观察者只会看到自洽的一组值，不会读到
 * "state 已变、error 还没变" 的中间态。
 */
public final class VpnSessionState {

    public static final VpnSessionState IDLE = new VpnSessionState(
            VpnStateService.State.DISABLED,
            VpnStateService.ErrorState.NO_ERROR,
            ImcState.UNKNOWN,
            false);

    private final VpnStateService.State state;
    private final VpnStateService.ErrorState errorState;
    private final ImcState imcState;
    /** 状态服务是否已连接上；未连接时 rest 字段不可信。 */
    private final boolean bound;

    public VpnSessionState(VpnStateService.State state,
                           VpnStateService.ErrorState errorState,
                           ImcState imcState,
                           boolean bound) {
        this.state = state == null ? VpnStateService.State.DISABLED : state;
        this.errorState = errorState == null ? VpnStateService.ErrorState.NO_ERROR : errorState;
        this.imcState = imcState == null ? ImcState.UNKNOWN : imcState;
        this.bound = bound;
    }

    public VpnStateService.State getState() {
        return state;
    }

    public VpnStateService.ErrorState getErrorState() {
        return errorState;
    }

    public ImcState getImcState() {
        return imcState;
    }

    public boolean isBound() {
        return bound;
    }

    public boolean isConnected() {
        return state == VpnStateService.State.CONNECTED;
    }

    public boolean isConnecting() {
        return state == VpnStateService.State.CONNECTING;
    }

    public boolean isDisconnecting() {
        return state == VpnStateService.State.DISCONNECTING;
    }

    public boolean isIdle() {
        return state == VpnStateService.State.DISABLED;
    }

    public boolean hasError() {
        return errorState != VpnStateService.ErrorState.NO_ERROR;
    }

    public VpnSessionState withBound(boolean bound) {
        return new VpnSessionState(state, errorState, imcState, bound);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VpnSessionState)) {
            return false;
        }
        VpnSessionState other = (VpnSessionState) o;
        return bound == other.bound
                && state == other.state
                && errorState == other.errorState
                && imcState == other.imcState;
    }

    @Override
    public int hashCode() {
        int result = state.hashCode();
        result = 31 * result + errorState.hashCode();
        result = 31 * result + imcState.hashCode();
        result = 31 * result + (bound ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "VpnSessionState{" + state + ", " + errorState + ", imc=" + imcState + ", bound=" + bound + '}';
    }
}
