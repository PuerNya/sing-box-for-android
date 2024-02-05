package io.nekohasekai.sfa.constant

import io.nekohasekai.sfa.R

enum class EnabledType(val boolValue: Boolean) {
    Enabled(true), Disabled(false);

    companion object {
        fun from(value: Boolean): EnabledType {
            return if (value) Enabled else Disabled
        }
        fun valueOf(value: String): EnabledType {
            return if (value == R.string.enabled) Enabled else Disabled 
        }
    }
}