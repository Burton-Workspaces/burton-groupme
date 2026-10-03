package com.burton.groupme.ui.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.burton.groupme.ui.theme.BurtonBlack
import com.burton.groupme.ui.theme.BurtonCharcoal
import com.burton.groupme.ui.theme.BurtonIvory
import com.burton.groupme.ui.theme.BurtonMute
import com.burton.groupme.ui.theme.BurtonSand
import com.burton.groupme.ui.theme.BurtonVoid

@Composable
fun SignInScreen(
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BurtonBlack)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Burton GroupMe", style = MaterialTheme.typography.headlineLarge, color = BurtonIvory)
        Spacer(Modifier.height(12.dp))
        Text(
            "Connect with GroupMe. The token stays on this phone; the app talks to GroupMe over HTTPS.",
            style = MaterialTheme.typography.bodyLarge,
            color = BurtonMute,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { viewModel.connectWithGroupMe { url -> openAuthorizeUrl(context, url) } },
            enabled = !ui.busy,
            colors = ButtonDefaults.buttonColors(containerColor = BurtonIvory, contentColor = BurtonVoid),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (ui.busy) {
                CircularProgressIndicator(color = BurtonSand, modifier = Modifier.height(18.dp))
            } else {
                Text("Connect with GroupMe")
            }
        }
        if (!ui.oauthConfigured) {
            Spacer(Modifier.height(12.dp))
            Text(
                "This build has no GroupMe Client ID yet. After creating an app at dev.groupme.com with HTTPS callback https://burton-workspaces.github.io/burton-groupme/oauth/, put the public Client ID in groupme/client-id.txt — or paste an access token below.",
                style = MaterialTheme.typography.bodyMedium,
                color = BurtonMute,
            )
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = viewModel::togglePaste, enabled = !ui.busy) {
            Text(
                if (ui.pasteOpen) "Hide token field" else "Use a token",
                color = BurtonSand,
            )
        }
        if (ui.pasteOpen) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BurtonCharcoal, RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Text("Access token", style = MaterialTheme.typography.labelSmall, color = BurtonMute)
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = ui.token,
                    onValueChange = viewModel::onTokenChange,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = MaterialTheme.typography.titleLarge.copy(color = BurtonIvory),
                    cursorBrush = SolidColor(BurtonIvory),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (ui.token.isBlank()) {
                            Text("Access token", color = BurtonMute, style = MaterialTheme.typography.titleLarge)
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Paste an access token from https://dev.groupme.com/. Prefer Connect with GroupMe when the Client ID is set.",
                style = MaterialTheme.typography.bodyMedium,
                color = BurtonMute,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = viewModel::connect,
                enabled = !ui.busy,
                colors = ButtonDefaults.buttonColors(containerColor = BurtonCharcoal, contentColor = BurtonIvory),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Connect with token")
            }
        }
        if (ui.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(ui.error ?: "", color = BurtonIvory, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
