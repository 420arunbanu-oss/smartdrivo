package com.example.security

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.example.BuildConfig
import java.security.MessageDigest

object SignatureIntegrity {

    // APK_SIGNATURE_TAMPER_LOCK_V2
    // Direct GitHub Actions release APKs are signed with the release/upload
    // certificate. Android Studio debug runs use the local debug certificate.
    // Release builds NEVER trust the debug certificate.
    private const val RELEASE_CERT_SHA256 = "0B8E390741179EA1B1F0A319FFE48247E2265342225E52AF93722656907C0959"
    private const val DEBUG_CERT_SHA256 = "0E24BE6A535CEDA44A30B86065041BA457DD9C4FBC242F2DFA80368D1DAF2504"

    private fun expectedCertSha256(): Set<String> =
        if (BuildConfig.DEBUG) {
            setOf(DEBUG_CERT_SHA256)
        } else {
            setOf(RELEASE_CERT_SHA256)
        }

    data class Result(
        val isValid: Boolean,
        val actualSha256: Set<String>
    )

    fun verify(context: Context): Result {
        return try {
            val actual =
                getPackageSignatures(context)
                    .map {
                        sha256(it.toByteArray())
                    }
                    .toSet()

            val expected =
                expectedCertSha256()

            Result(
                isValid =
                    actual.isNotEmpty() &&
                        actual.any {
                            expected.contains(it)
                        },
                actualSha256 = actual
            )
        } catch (_: Exception) {
            // Fail closed: if Android cannot verify the certificate,
            // assistant access is not allowed.
            Result(
                isValid = false,
                actualSha256 = emptySet()
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun getPackageSignatures(
        context: Context
    ): List<Signature> {
        val pm = context.packageManager
        val packageName = context.packageName

        return if (
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.P
        ) {
            val info =
                pm.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )

            val signingInfo =
                info.signingInfo
                    ?: return emptyList()

            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
                    ?.toList()
                    .orEmpty()
            } else {
                signingInfo.signingCertificateHistory
                    ?.toList()
                    .orEmpty()
            }
        } else {
            pm.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNATURES
            ).signatures
                ?.toList()
                .orEmpty()
        }
    }

    private fun sha256(
        bytes: ByteArray
    ): String {
        return MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") {
                "%02X".format(it)
            }
    }
}
