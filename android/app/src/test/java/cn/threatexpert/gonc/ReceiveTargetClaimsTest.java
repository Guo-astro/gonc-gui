package cn.threatexpert.gonc;

import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class ReceiveTargetClaimsTest {
    @Test public void sanitizedPathCollisionIsRejected() throws Exception {
        ReceiveTargetClaims claims = new ReceiveTargetClaims();
        claims.claimPath("a_b.jpg", "a:b.jpg");
        try { claims.claimPath("a_b.jpg", "a?b.jpg"); fail("collision"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("a:b.jpg")); }
    }

    @Test public void resumeVariantCannotReuseAnotherJobsUri() throws Exception {
        ReceiveTargetClaims claims = new ReceiveTargetClaims();
        claims.claimUri("content://media/downloads/42", "photo.jpg");
        try { claims.checkUri("content://media/downloads/42", "photo (1).jpg"); fail("collision"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("photo.jpg")); }
    }

    @Test public void sameOwnerIsIdempotentAndSeparateDirectoriesAreIndependent() throws Exception {
        ReceiveTargetClaims claims = new ReceiveTargetClaims();
        claims.claimPath("one/photo.jpg", "one/photo.jpg");
        claims.claimPath("two/photo.jpg", "two/photo.jpg");
        claims.claimUri("uri-one", "one/photo.jpg");
        claims.claimUri("uri-one", "one/photo.jpg");
        claims.claimUri("uri-two", "two/photo.jpg");
    }
}
