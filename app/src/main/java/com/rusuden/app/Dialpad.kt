package com.rusuden.app

import android.view.View

object Dialpad {
    private val keys = mapOf(
        R.id.key_0 to '0',
        R.id.key_1 to '1',
        R.id.key_2 to '2',
        R.id.key_3 to '3',
        R.id.key_4 to '4',
        R.id.key_5 to '5',
        R.id.key_6 to '6',
        R.id.key_7 to '7',
        R.id.key_8 to '8',
        R.id.key_9 to '9',
        R.id.key_star to '*',
        R.id.key_hash to '#'
    )

    fun bind(root: View, onKey: (Char) -> Unit) {
        for ((id, ch) in keys) {
            root.findViewById<View>(id)?.setOnClickListener { onKey(ch) }
        }
    }

    /** 0キー長押しで「+」を入力できるようにする(国際電話用)。 */
    fun bindPlusOnZeroLongPress(root: View, onPlus: () -> Unit) {
        root.findViewById<View>(R.id.key_0)?.setOnLongClickListener {
            onPlus()
            true
        }
    }
}
