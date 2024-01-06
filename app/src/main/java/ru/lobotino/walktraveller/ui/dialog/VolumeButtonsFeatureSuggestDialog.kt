package ru.lobotino.walktraveller.ui.dialog

import android.content.Context
import ru.lobotino.walktraveller.R

class VolumeButtonsFeatureSuggestDialog(context: Context, onYesClicked: () -> Unit, onNoClicked: () -> Unit) : ConfirmYesNoDialog(
    context,
    context.getString(R.string.volume_buttons_feature_request_title),
    context.getString(R.string.volume_buttons_feature_request_desc),
    onYesClicked,
    onNoClicked
)