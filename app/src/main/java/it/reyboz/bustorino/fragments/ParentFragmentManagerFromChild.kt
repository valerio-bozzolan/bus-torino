package it.reyboz.bustorino.fragments


interface ParentFragmentManagerFromChild {

    fun needToPopMainStackOnBack() : Boolean

    fun setMainFragmentManagerTransition(yes: Boolean)
}