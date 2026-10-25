package com.mango.products;

import reactor.blockhound.BlockHound;
import reactor.blockhound.integration.BlockHoundIntegration;

/**
 * Known, bounded blocking inside libraries, never in our code:
 * the Mongo driver seeds session ids from SecureRandom and guards its pool with short-lived locks,
 * and Hibernate Validator loads its message bundle on the first violation.
 */
public class ThirdPartyBlockingAllowList implements BlockHoundIntegration {

	@Override
	public void applyTo(BlockHound.Builder builder) {
		builder.allowBlockingCallsInside("com.mongodb.internal.session.ServerSessionPool", "createNewServerSessionIdentifier")
				.allowBlockingCallsInside("com.mongodb.internal.Locks", "checkedWithLock")
				.allowBlockingCallsInside("com.mongodb.internal.Locks", "checkedWithInterruptibleLock")
				.allowBlockingCallsInside("org.hibernate.validator.resourceloading.PlatformResourceBundleLocator", "loadBundle");
	}

}
