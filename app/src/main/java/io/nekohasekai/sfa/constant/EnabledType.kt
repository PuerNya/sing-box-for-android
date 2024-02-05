package io.nekohasekai.sfa.constant

import io.nekohasekai.sfa.R

enum class EnabledType(val boolValue: Boolean, keyName: String) {
    Enabled(true, getString(R.string.enabled)), Disabled(false, getString(R.string.Disabled));

    companion object {
        fun from(value: Boolean): EnabledType {
            return if (value) Enabled else Disabled
        }
        fun getType(value: String): EnabledType {
            return if (value == getString(R.string.enabled)) Enabled else Disabled 
        }
    }
}