package it.reyboz.bustorino.fragments;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

public abstract class ResultBaseFragment extends ScreenBaseFragment {

    protected FragmentListenerMain mListener;
    protected static final String MESSAGE_TEXT_VIEW = "message_text_view";


    public ResultBaseFragment() {
    }
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof FragmentListenerMain) {
            mListener = (FragmentListenerMain) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement FragmentListenerMain");
        }

    }

    @Override
    public void onDetach() {
        mListener.showFloatingActionButton(false);
        mListener = null;
        super.onDetach();
    }
}
