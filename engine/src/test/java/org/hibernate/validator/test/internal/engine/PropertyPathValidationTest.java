/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.validator.test.internal.engine;

import static org.hibernate.validator.testutils.ValidatorUtil.getValidator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;

import org.hibernate.validator.testutil.TestForIssue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@TestForIssue(jiraKey = "HV-1316")
public class PropertyPathValidationTest {
	private final Validator validator = getValidator();

	public static Object[][] nonCascadingRoots() {
		return new Object[][] { { new PlainRoot() }, { new ConstrainedRoot() } };
	}

	@ParameterizedTest
	@MethodSource("nonCascadingRoots")
	public void intermediateAssociationRequiresValid(Object root) {
		for ( String path : new String[] { "leaf.required", "leaf.optional", "leaf.missing" } ) {
			assertThrows( IllegalArgumentException.class, () -> validator.validateProperty( root, path ) );
		}
	}

	@ParameterizedTest
	@MethodSource("nonCascadingRoots")
	public void directExistingPropertyWithoutApplicableConstraints(Object root) {
		assertTrue( validator.validateProperty( root, "leaf" ).isEmpty() );
		assertTrue( validator.validateValue( root.getClass(), "leaf", new Leaf() ).isEmpty() );
	}

	@ParameterizedTest
	@MethodSource("nonCascadingRoots")
	public void missingRootPropertyIsRejected(Object root) {
		assertThrows( IllegalArgumentException.class, () -> validator.validateProperty( root, "missing" ) );
		assertThrows( IllegalArgumentException.class, () -> validator.validateValue( root.getClass(), "missing", null ) );
	}

	@Test
	public void nestedLeafConstraintsAndViolationContext() {
		CascadingRoot root = new CascadingRoot();
		var violations = validator.validateProperty( root, "leaf.required" );
		assertEquals( 1, violations.size() );
		var violation = violations.iterator().next();
		assertSame( root, violation.getRootBean() );
		assertSame( root.leaf, violation.getLeafBean() );
		assertEquals( "leaf.required", violation.getPropertyPath().toString() );
		assertTrue( validator.validateProperty( root, "leaf.optional" ).isEmpty() );
		assertTrue( validator.validateProperty( root, "leaf" ).isEmpty() );
		assertThrows( IllegalArgumentException.class, () -> validator.validateProperty( root, "leaf.missing" ) );
	}

	@Test
	public void everyIntermediateAssociationRequiresValid() {
		DeepRoot root = new DeepRoot();
		assertEquals( 1, validator.validateProperty( root, "branch.leaf.required" ).size() );
		assertTrue( validator.validateProperty( root, "branch.leaf.optional" ).isEmpty() );
		assertThrows( IllegalArgumentException.class, () -> validator.validateProperty( root, "branch.unmarked.required" ) );
	}

	@Test
	public void leafCanBeValidatedDirectly() {
		Leaf leaf = new Leaf();
		var violation = validator.validateProperty( leaf, "required" ).iterator().next();
		assertSame( leaf, violation.getRootBean() );
		assertEquals( "required", violation.getPropertyPath().toString() );
		assertTrue( validator.validateProperty( leaf, "optional" ).isEmpty() );
	}

	@Test
	public void entirelyUnconstrainedLeafReturnsEmptyForExistingProperty() {
		assertTrue( validator.validateProperty( new PlainLeaf(), "optional" ).isEmpty() );
		assertTrue( validator.validateValue( PlainLeaf.class, "optional", null ).isEmpty() );
		assertTrue( validator.validateProperty( new PlainLeafRoot(), "leaf.optional" ).isEmpty() );
		assertThrows( IllegalArgumentException.class, () -> validator.validateProperty( new PlainLeafRoot(), "leaf.missing" ) );
	}

	public static class Leaf {
		@NotNull
		public String required;
		@NotNull
		public String otherRequired;
		public String optional;
	}

	public static class PlainRoot {
		public Leaf leaf = new Leaf();
	}

	public static class ConstrainedRoot {
		@NotNull
		public String unrelated = "present";
		public Leaf leaf = new Leaf();
	}

	public static class CascadingRoot {
		@Valid
		public Leaf leaf = new Leaf();
	}

	public static class Branch extends CascadingRoot {
		public Leaf unmarked = new Leaf();
	}

	public static class DeepRoot {
		@Valid
		public Branch branch = new Branch();
	}

	public static class PlainLeaf {
		public String optional;
	}

	public static class PlainLeafRoot {
		@Valid
		public PlainLeaf leaf = new PlainLeaf();
	}
}
