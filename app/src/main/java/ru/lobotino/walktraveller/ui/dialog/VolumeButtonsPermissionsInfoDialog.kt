package ru.lobotino.walktraveller.ui.dialog

import android.content.Context
import ru.lobotino.walktraveller.R

class VolumeButtonsPermissionsInfoDialog(context: Context, onYesClicked: () -> Unit) : ConfirmYesNoDialog(
    context,
    context.getString(R.string.volume_buttons_feature_info_title),
    context.getString(R.string.volume_buttons_feature_info_desc),
    onYesClicked,
    null
)
