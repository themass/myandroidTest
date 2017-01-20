package com.timeline.vpn.ui.base;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.common.util.ViewUtils;

import java.io.Serializable;

import butterknife.ButterKnife;

/**
 * 所有的Fragment基类
 */
public abstract class BaseFragment extends LogFramgment {
    public static final String FRAGMENT_ARG = "ARG";
    protected View rootView;
    Bundle savedState;

    /**
     * 传递参数时，   需要传的序列化数据
     *
     * @param data 序列化的数据
     */
    public void putSerializable(Serializable data) {
        Bundle bundle = new Bundle();
        bundle.putSerializable(FRAGMENT_ARG, data);
        setArguments(bundle);
    }

    /**
     * Get the Serializable paramters ,which is from  {@link #putSerializable(Serializable data)} to move a piece.
     *
     * @return
     */
    public Serializable getSerializable() {
        Bundle bundle = getArguments();
        if (bundle != null) {
            return bundle.getSerializable(FRAGMENT_ARG);
        }
        return null;
    }

    /**
     * 通过id来查找view中的子View
     *
     * @param v   父View
     * @param id  子View的id
     * @param <T> 子view的类型，可以不传
     * @return
     */
    public <T extends View> T v(View v, int id) {
        return ViewUtils.find(v, id);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View view = setContentView(inflater);
        rootView = view;
        if (view != null) {
            setupViews(view, savedInstanceState);
        }
        return view;
    }

    protected View setContentView(LayoutInflater inflater) {
        return inflater.inflate(getRootViewId(), null);
    }

    /**
     * Set a layout ID for the Fragment
     *
     * @return
     */
    protected abstract int getRootViewId();

    /**
     * setup the view
     *
     * @param view
     */
    protected void setupViews(View view, Bundle savedInstanceState) {
        bindViews(view);
        LogUtil.i(getClass().getSimpleName() + "-setupViews");
    }

    protected void bindViews(View view) {
        ButterKnife.bind(this, view);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Restore State Here
        if (!restoreStateFromArguments()) {
            // First Time, Initialize something here
            onFirstTimeLaunched();
        }
    }

    /**
     * Called when the fragment is launched for the first time.
     * In the other words, fragment is now recreated.
     */

    protected void onFirstTimeLaunched() {

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // Save State Here
        saveStateToArguments();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Save State Here
        saveStateToArguments();
        ButterKnife.unbind(this);
//        RefWatcher refWatcher = MyApplication.getRefWatcher(getActivity());
//        refWatcher.watch(this);
    }

    ////////////////////
    // Don't Touch !!
    ////////////////////

    private void saveStateToArguments() {
        if (getView() != null)
            savedState = saveState();
        if (savedState != null) {
            Bundle b = getArguments();
            if (b != null)
                b.putBundle("internalSavedViewState8954201239547", savedState);
        }
    }

    ////////////////////
    // Don't Touch !!
    ////////////////////

    private boolean restoreStateFromArguments() {
        Bundle b = getArguments();
        if (b != null) {
            savedState = b.getBundle("internalSavedViewState8954201239547");
            if (savedState != null) {
                restoreState();
                return true;
            }
        }
        return false;
    }

    /////////////////////////////////
    // Restore Instance State Here
    /////////////////////////////////

    private void restoreState() {
        if (savedState != null) {
            // For Example
            //tv1.setText(savedState.getString("text"));
            onRestoreState(savedState);
        }
    }


    /**
     * Called when the fragment's activity has been created and this
     * fragment's view hierarchy instantiated.  It can be used to do final
     * initialization once these pieces are in place, such as retrieving
     * views or restoring state.  This is called after {@link #onCreateView}
     * and before {@link #onViewStateRestored(Bundle)}.
     *
     * @param savedInstanceState If the fragment is being re-created from
     *                           a previous saved state, this is the state.
     */

    protected void onRestoreState(Bundle savedInstanceState) {

    }

    //////////////////////////////
    // Save Instance State Here
    //////////////////////////////

    private Bundle saveState() {
        Bundle state = new Bundle();
        // For Example
        //state.putString("text", tv1.getText().toString());
        onSaveState(state);
        return state;
    }

    /**
     * Called to ask the fragment to save its current dynamic state, so it
     * can later be reconstructed in a new instance of its process is
     * restarted.  If a new instance of the fragment later needs to be
     * created, the data you place in the Bundle here will be available
     * in the Bundle given to {@link #onRestoreState(Bundle)}.
     * <p/>
     * <p>This corresponds to {@link android.app.Activity#onSaveInstanceState(Bundle)
     * Activity.onSaveInstanceState(Bundle)} and most of the discussion there
     * applies here as well.  Note however: <em>this method may be called
     * at any time before {@link #onDestroy()}</em>.  There are many situations
     * where a fragment may be mostly torn down (such as when placed on the
     * back stack with no UI showing), but its state will not be saved until
     * its owning activity actually needs to save its state.
     *
     * @param outState Bundle in which to place your saved state.
     */

    protected void onSaveState(Bundle outState) {

    }

    public void startActivity(Class clasz) {
        Intent intent = new Intent(getActivity(), clasz);
        getActivity().startActivity(intent);
    }

}
