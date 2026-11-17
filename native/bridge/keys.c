#include <jni.h>
#include <openssl/bn.h>
#include <openssl/ec.h>
#include <openssl/obj_mac.h>
#include <openssl/crypto.h>

JNIEXPORT jbyteArray JNICALL Java_com_koinos_teleno_NativeKeys_publicKey(JNIEnv *env, jclass klass, jbyteArray input) {
    (void)klass;
    unsigned char secret[32] = {0}, pub[33] = {0};
    BIGNUM *d = NULL, *order = NULL;
    EC_GROUP *group = NULL; EC_POINT *point = NULL; BN_CTX *ctx = NULL;
    jbyteArray out = NULL;
    if (input == NULL || (*env)->GetArrayLength(env, input) != 32) goto done;
    (*env)->GetByteArrayRegion(env, input, 0, 32, (jbyte*)secret);
    if ((*env)->ExceptionCheck(env)) goto done;
    d = BN_bin2bn(secret, 32, NULL); order = BN_new(); ctx = BN_CTX_new();
    group = EC_GROUP_new_by_curve_name(NID_secp256k1);
    if (!d || !order || !ctx || !group) goto done;
    if (!EC_GROUP_get_order(group, order, ctx) || BN_is_zero(d) || BN_cmp(d, order) >= 0) goto done;
    BN_set_flags(d, BN_FLG_CONSTTIME);
    point = EC_POINT_new(group);
    if (!point || !EC_POINT_mul(group, point, d, NULL, NULL, ctx)) goto done;
    if (EC_POINT_point2oct(group, point, POINT_CONVERSION_COMPRESSED, pub, sizeof(pub), ctx) != 33) goto done;
    out = (*env)->NewByteArray(env, 33);
    if (out) (*env)->SetByteArrayRegion(env, out, 0, 33, (jbyte*)pub);
 done:
    OPENSSL_cleanse(secret, sizeof(secret));
    BN_clear_free(d); BN_free(order); EC_POINT_free(point); EC_GROUP_free(group); BN_CTX_free(ctx);
    if (!out && !(*env)->ExceptionCheck(env)) (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"), "The block-signing private key is invalid or cryptography is unavailable.");
    return out;
}
