package ir.lbo.locationsms

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.biometric.BiometricManager

class SecuritySettingsActivity : LockProtectedActivity() {

    private lateinit var settings: SettingsRepository
    private lateinit var currentPasswordInput: EditText
    private lateinit var newPasswordInput: EditText
    private lateinit var confirmNewPasswordInput: EditText
    private lateinit var errorText: TextView
    private lateinit var biometricSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security_settings)

        settings = SettingsRepository(this)
        currentPasswordInput = findViewById(R.id.currentPasswordInput)
        newPasswordInput = findViewById(R.id.newPasswordInput)
        confirmNewPasswordInput = findViewById(R.id.confirmNewPasswordInput)
        errorText = findViewById(R.id.securityErrorText)
        biometricSwitch = findViewById(R.id.biometricSwitch)

        val biometricAvailable = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

        biometricSwitch.isEnabled = biometricAvailable
        biometricSwitch.isChecked = settings.isBiometricEnabled() && biometricAvailable
        if (!biometricAvailable) {
            biometricSwitch.text = getString(R.string.security_settings_biometric_unavailable)
        }

        biometricSwitch.setOnCheckedChangeListener { _, isChecked ->
            settings.saveBiometricEnabled(isChecked)
            Toast.makeText(
                this,
                if (isChecked) getString(R.string.security_settings_biometric_enabled) else getString(R.string.security_settings_biometric_disabled),
                Toast.LENGTH_SHORT
            ).show()
        }

        findViewById<Button>(R.id.changePasswordButton).setOnClickListener {
            onChangePasswordClicked()
        }
    }

    private fun onChangePasswordClicked() {
        errorText.text = ""
        val currentPassword = currentPasswordInput.text.toString()
        val newPassword = newPasswordInput.text.toString()
        val confirmPassword = confirmNewPasswordInput.text.toString()

        if (!settings.verifyPassword(currentPassword)) {
            errorText.text = getString(R.string.security_settings_error_current_wrong)
            return
        }

        if (newPassword.length < 4) {
            errorText.text = getString(R.string.security_settings_error_new_too_short)
            return
        }

        if (newPassword != confirmPassword) {
            errorText.text = getString(R.string.security_settings_error_confirm_mismatch)
            return
        }

        settings.setPassword(newPassword)
        currentPasswordInput.text?.clear()
        newPasswordInput.text?.clear()
        confirmNewPasswordInput.text?.clear()
        Toast.makeText(this, getString(R.string.security_settings_success), Toast.LENGTH_SHORT).show()
    }
}
