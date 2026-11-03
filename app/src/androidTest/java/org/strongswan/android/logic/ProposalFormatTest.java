package org.strongswan.android.logic;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.strongswan.android.logic.utils.Utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 用 native 的 proposal_create_from_string 校验实际下发的提案串。
 * <p>
 * 这条断言的意义：提案串写错时 strongSwan 不会报错，只会静默回退到默认算法，
 * 因此必须有一个会失败的测试来守住它。
 */
@RunWith(AndroidJUnit4.class)
public class ProposalFormatTest {

    /**
     * isProposalValid 位于 libandroidbridge，测试进程不会经过 CharonVpnService
     * 的静态块，因此必须在这里显式加载，否则会抛 UnsatisfiedLinkError。
     * 只加载 bridge 与其依赖，不需要 charon 主线程。
     */
    @BeforeClass
    public static void loadNativeLibrary() {
        System.loadLibrary("strongswan");
        System.loadLibrary("androidbridge");
    }

    // 与 CharonVpnService.applyProposals() 的 tier 0 保持一致
    private static final String TIER0_IKE =
            "aes256gcm16-prfsha384-ecp384-prfsha256-ecp256-prfsha256-curve25519";
    private static final String TIER0_ESP =
            "aes256gcm16-ecp384-ecp256-chacha20poly1305-curve25519";

    // tier 1（兼容档）
    private static final String TIER1_IKE =
            "aes256-sha256-modp2048-aes128-sha256-modp2048";
    private static final String TIER1_ESP =
            "aes256-sha256-aes128-sha256";

    @Test
    public void tier0ProposalsAreParseable() {
        assertTrue("tier0 IKE proposal must parse", Utils.isProposalValid(true, TIER0_IKE));
        assertTrue("tier0 ESP proposal must parse", Utils.isProposalValid(false, TIER0_ESP));
    }

    @Test
    public void tier1ProposalsAreParseable() {
        assertTrue("tier1 IKE proposal must parse", Utils.isProposalValid(true, TIER1_IKE));
        assertTrue("tier1 ESP proposal must parse", Utils.isProposalValid(false, TIER1_ESP));
    }

    /** 旧的失效配置：逗号列表 + 不存在的 charon.plugins.ikev2 键。 */
    @Test
    public void formerProductionValueWasBroken() {
        String old = "aes256gcm16-sha256-modp2048,aes256-sha256-modp2048,aes256-sha1-modp2048";
        assertFalse("comma-separated list must not parse as a single proposal",
                Utils.isProposalValid(true, old));
    }

    /** '/' 只是输出格式，写进输入会整条解析失败。 */
    @Test
    public void slashSeparatorIsNotValidInput() {
        assertFalse("slash-separated alternatives must not parse",
                Utils.isProposalValid(true, "aes256gcm16-prfsha384-ecp384/aes256gcm16-prfsha256-ecp256"));
    }

    /** AEAD 与 CBC 混在同一条提案里会被 check_proposal 拒绝。 */
    @Test
    public void aeadAndCbcCannotBeMixed() {
        assertFalse("AEAD + CBC mix must be rejected",
                Utils.isProposalValid(false, "aes128gcm16-aes128-sha256-modp3072"));
    }
}
