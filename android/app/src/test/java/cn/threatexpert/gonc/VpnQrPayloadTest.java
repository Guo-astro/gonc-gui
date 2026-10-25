package cn.threatexpert.gonc;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class VpnQrPayloadTest {
    private JSONObject config() throws Exception {
        return new JSONObject().put("type", VpnQrPayload.TYPE).put("version", 1)
                .put("profile", new JSONObject().put("name", "Test")
                        .put("passphrase", "Abc12345").put("mtu", 1400).put("useUdp", false));
    }

    private void invalid(String value) {
        VpnQrPayload parsed = VpnQrPayload.parse(value);
        assertEquals(VpnQrPayload.Kind.INVALID, parsed.kind);
        assertNull(parsed.passphrase);
        assertNull(parsed.profile);
    }

    @Test public void ordinaryAndCustomPassphrasesRemainCompatible() {
        for (String value : new String[]{"Abc12345", "custom-passphrase!", "自定义口令123"}) {
            VpnQrPayload parsed = VpnQrPayload.parse("  " + value + "  ");
            assertEquals(VpnQrPayload.Kind.PASSPHRASE, parsed.kind);
            assertEquals(value, parsed.passphrase);
            assertNull(parsed.profile);
        }
    }

    @Test public void exportedProfileIsRecognized() throws Exception {
        VpnQrPayload parsed = VpnQrPayload.parse(config().toString());
        assertEquals(VpnQrPayload.Kind.PROFILE, parsed.kind);
        assertEquals("Abc12345", parsed.profile.getString("passphrase"));
        assertNull(parsed.passphrase);
    }

    @Test public void emptyInputIsRejected() {
        invalid(null);
        invalid("   ");
    }

    @Test public void damagedConfigurationNeverBecomesPassphrase() throws Exception {
        invalid("{broken");
        invalid("[1,2]");
        invalid("broken gonc.vpn.profile payload");
        invalid(config().toString() + " trailing junk");
        invalid("{}");
    }

    @Test public void unknownTypeIsRejected() throws Exception {
        invalid(config().put("type", "another.profile").toString());
    }

    @Test public void unsupportedVersionIsDistinctAndNotPassphrase() throws Exception {
        VpnQrPayload parsed = VpnQrPayload.parse(config().put("version", 2).toString());
        assertEquals(VpnQrPayload.Kind.UNSUPPORTED_VERSION, parsed.kind);
        assertNull(parsed.passphrase);
    }

    @Test public void missingOrInvalidVersionIsRejected() throws Exception {
        JSONObject root = config();
        root.remove("version");
        invalid(root.toString());
        invalid(config().put("version", "1").toString());
        invalid(config().put("version", 1.5).toString());
    }

    @Test public void missingProfileOrPassphraseIsRejected() throws Exception {
        invalid(config().put("profile", "bad").toString());
        invalid(config().put("profile", new JSONObject()).toString());
        for (Object value : new Object[]{"  ", 123, JSONObject.NULL}) {
            JSONObject root = config();
            root.getJSONObject("profile").put("passphrase", value);
            invalid(root.toString());
        }
    }

    @Test public void wrongOptionalFieldTypesAreRejected() throws Exception {
        for (String key : new String[]{"name", "dnsServers", "routeCidrs", "linkConfig", "socks5Address", "extraArgs", "useUdp", "routeIpv6", "tunnelOnly", "mtu"}) {
            JSONObject root = config();
            root.getJSONObject("profile").put(key, new JSONObject());
            invalid(root.toString());
        }
    }

    @Test public void invalidMtuIsRejected() throws Exception {
        for (Object value : new Object[]{0, 9001, 1400.5, "1400"}) {
            JSONObject root = config();
            root.getJSONObject("profile").put("mtu", value);
            invalid(root.toString());
        }
    }

    @Test public void optionalFieldsAndLegacyLinkRemainCompatible() throws Exception {
        JSONObject root = config().put("profile", new JSONObject()
                .put("passphrase", "Abc12345").put("socks5Address", "3080"));
        assertEquals(VpnQrPayload.Kind.PROFILE, VpnQrPayload.parse(root.toString()).kind);
    }
}
