package com.prithon.dpc.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.prithon.dpc.ui.theme.Blue50
import com.prithon.dpc.ui.theme.Blue600
import com.prithon.dpc.ui.theme.PrithonDpcTheme
import com.prithon.dpc.ui.theme.Slate50
import com.prithon.dpc.ui.theme.Slate500

class AdminPolicyComplianceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Log.i("AdminCompliance", "ADMIN_POLICY_COMPLIANCE screen launched by SetupWizard.")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                completeSetup()
            }
        })

        setContent {
            PrithonDpcTheme {
                Surface(
                    color = Slate50,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            color = Blue50,
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                tint = Blue600,
                                modifier = Modifier
                                    .padding(20.dp)
                                    .size(56.dp)
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = "Enterprise Policy Compliance",
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Prithon Device Guard has configured your financed device policies. Tap below to finalize Android Enterprise setup.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Slate500,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(32.dp))
                        Button(
                            onClick = ::completeSetup,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text("COMPLETE SETUP")
                        }
                    }
                }
            }
        }
    }

    private fun completeSetup() {
        Log.i("AdminCompliance", "Returning RESULT_OK to SetupWizard.")
        val resultIntent = Intent()
        if (intent != null) {
            resultIntent.putExtras(intent)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}
