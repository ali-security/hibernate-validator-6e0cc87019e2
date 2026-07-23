/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.validator.test.internal.constraintvalidators.bv;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hibernate.validator.testutils.ConstraintValidatorInitializationHelper.initialize;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import java.util.regex.PatternSyntaxException;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import jakarta.validation.constraints.Pattern;

import org.hibernate.validator.internal.constraintvalidators.bv.PatternValidator;
import org.hibernate.validator.internal.util.annotation.ConstraintAnnotationDescriptor;
import org.hibernate.validator.testutil.MyCustomStringImpl;
import org.hibernate.validator.testutil.TestForIssue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * @author Hardy Ferentschik
 */
public class PatternValidatorTest {

	@ParameterizedTest
	@MethodSource("testValidData")
	public void testValid(String value) {
		ConstraintAnnotationDescriptor.Builder<Pattern> descriptorBuilder = new ConstraintAnnotationDescriptor.Builder<>( Pattern.class );
		descriptorBuilder.setAttribute( "regexp", "foobar" );
		descriptorBuilder.setMessage( "pattern does not match" );
		ConstraintAnnotationDescriptor<Pattern> descriptor = descriptorBuilder.build();

		PatternValidator constraint = new PatternValidator();
		initialize( constraint, descriptor );

		assertTrue( constraint.isValid( value, null ) );
	}

	private static Stream<Arguments> testValidData() {
		return Stream.of(
				Arguments.of( (String) null )
		);
	}

	@ParameterizedTest
	@MethodSource("testInvalidData")
	public void testInvalid(String value) {
		ConstraintAnnotationDescriptor.Builder<Pattern> descriptorBuilder = new ConstraintAnnotationDescriptor.Builder<>( Pattern.class );
		descriptorBuilder.setAttribute( "regexp", "foobar" );
		descriptorBuilder.setMessage( "pattern does not match" );
		ConstraintAnnotationDescriptor<Pattern> descriptor = descriptorBuilder.build();

		PatternValidator constraint = new PatternValidator();
		initialize( constraint, descriptor );

		assertFalse( constraint.isValid( value, null ) );
	}

	private static Stream<Arguments> testInvalidData() {
		return Stream.of(
				Arguments.of( "" ),
				Arguments.of( "bla bla" ),
				Arguments.of( "This test is not foobar" )
		);
	}

	@Test
	@TestForIssue(jiraKey = "HV-502")
	public void testIsValidForCharSequence() {
		ConstraintAnnotationDescriptor.Builder<Pattern> descriptorBuilder = new ConstraintAnnotationDescriptor.Builder<>( Pattern.class );
		descriptorBuilder.setAttribute( "regexp", "char sequence" );
		ConstraintAnnotationDescriptor<Pattern> descriptor = descriptorBuilder.build();

		PatternValidator constraint = new PatternValidator();
		initialize( constraint, descriptor );

		assertTrue( constraint.isValid( new MyCustomStringImpl( "char sequence" ), null ) );
	}

	@ParameterizedTest
	@MethodSource("testValidForEmptyStringRegexpData")
	public void testValidForEmptyStringRegexp(String value) {
		ConstraintAnnotationDescriptor.Builder<Pattern> descriptorBuilder = new ConstraintAnnotationDescriptor.Builder<>( Pattern.class );
		descriptorBuilder.setAttribute( "regexp", "|^.*foo$" );
		descriptorBuilder.setMessage( "pattern does not match" );
		ConstraintAnnotationDescriptor<Pattern> descriptor = descriptorBuilder.build();

		PatternValidator constraint = new PatternValidator();
		initialize( constraint, descriptor );

		assertTrue( constraint.isValid( value, null ) );
	}

	private static Stream<Arguments> testValidForEmptyStringRegexpData() {
		return Stream.of(
				Arguments.of( (String) null ),
				Arguments.of( "" ),
				Arguments.of( "foo" ),
				Arguments.of( "a b c foo" )
		);
	}

	@ParameterizedTest
	@MethodSource("testInvalidForEmptyStringRegexpData")
	public void testInvalidForEmptyStringRegexp(String value) {
		ConstraintAnnotationDescriptor.Builder<Pattern> descriptorBuilder = new ConstraintAnnotationDescriptor.Builder<>( Pattern.class );
		descriptorBuilder.setAttribute( "regexp", "|^.*foo$" );
		descriptorBuilder.setMessage( "pattern does not match" );
		ConstraintAnnotationDescriptor<Pattern> descriptor = descriptorBuilder.build();

		PatternValidator constraint = new PatternValidator();
		initialize( constraint, descriptor );

		assertFalse( constraint.isValid( value, null ) );
	}

	private static Stream<Arguments> testInvalidForEmptyStringRegexpData() {
		return Stream.of(
				Arguments.of( "bla bla" )
		);
	}

	@Test
	public void testInvalidRegularExpression() {
		ConstraintAnnotationDescriptor.Builder<Pattern> descriptorBuilder = new ConstraintAnnotationDescriptor.Builder<>( Pattern.class );
		descriptorBuilder.setAttribute( "regexp", "(unbalanced parentheses" );
		descriptorBuilder.setMessage( "pattern does not match" );
		ConstraintAnnotationDescriptor<Pattern> descriptor = descriptorBuilder.build();

		PatternValidator constraint = new PatternValidator();
		IllegalArgumentException exception = expectThrows( IllegalArgumentException.class, () -> initialize( constraint, descriptor ) );
		assertTrue( exception.getMessage().startsWith( "HV000022:" ) );
		assertTrue( exception.getCause() instanceof PatternSyntaxException );
		assertEquals( ( (PatternSyntaxException) exception.getCause() ).getPattern(), "(unbalanced parentheses" );
	}

	@Test
	public void testCombinedFlagsStillRequireFullMatch() {
		ConstraintAnnotationDescriptor.Builder<Pattern> descriptor = new ConstraintAnnotationDescriptor.Builder<>( Pattern.class );
		descriptor.setAttribute( "regexp", "föö # ignored comment" );
		descriptor.setAttribute( "flags", new Pattern.Flag[] { Pattern.Flag.CASE_INSENSITIVE, Pattern.Flag.COMMENTS, Pattern.Flag.UNICODE_CASE } );
		PatternValidator constraint = new PatternValidator();
		initialize( constraint, descriptor.build() );

		assertTrue( constraint.isValid( "FÖÖ", null ) );
		assertTrue( constraint.isValid( new StringBuilder( "FÖÖ" ), null ) );
		assertFalse( constraint.isValid( "prefixFÖÖ", null ) );
		assertFalse( constraint.isValid( "FÖÖsuffix", null ) );
		assertFalse( constraint.isValid( "FÖÖ\n", null ) );
		assertFalse( constraint.isValid( "", null ) );
	}
}
