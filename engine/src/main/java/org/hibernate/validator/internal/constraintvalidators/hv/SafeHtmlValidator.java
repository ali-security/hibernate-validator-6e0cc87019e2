/*
 * Hibernate Validator, declare and validate application constraints
 *
 * License: Apache License, Version 2.0
 * See the license.txt file in the root directory or <http://www.apache.org/licenses/LICENSE-2.0>.
 */
package org.hibernate.validator.internal.constraintvalidators.hv;

import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.List;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import org.hibernate.validator.constraints.SafeHtml;
import org.hibernate.validator.internal.util.ReflectionHelper;
import org.hibernate.validator.internal.util.privilegedactions.GetMethod;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.jsoup.parser.ParseError;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Whitelist;

/**
 * Validate that the string does not contain malicious code.
 *
 * It uses <a href="http://www.jsoup.org">JSoup</a> as the underlying parser/sanitizer library.
 *
 * @author George Gastaldi
 * @author Hardy Ferentschik
 * @author Marko Bekhta
 * @deprecated {@code @SafeHtml} support will be removed in a future version
 */
@Deprecated
public class SafeHtmlValidator implements ConstraintValidator<SafeHtml, CharSequence> {

	/**
	 * The maximum number of parse errors tracked while parsing a value. If this number is reached, the value is
	 * considered unsafe as not all the parse errors could be inspected.
	 */
	private static final int MAX_TRACKED_PARSE_ERRORS = 10_000;

	/**
	 * Error reported by jsoup for a self-closing tag directly following some text (e.g. {@code text<br/>}), even
	 * though the tag has been processed properly. It does not denote a malformed input, so it is ignored.
	 */
	private static final String SELF_CLOSING_FLAG_NOT_ACKNOWLEDGED_ERROR = "Self closing flag not acknowledged";

	/**
	 * {@code Parser#getErrors()} is invoked reflectively as its return type differs between jsoup versions
	 * ({@code List} vs. {@code ParseErrorList}).
	 */
	private static final Method GET_PARSE_ERRORS_METHOD = run( GetMethod.action( Parser.class, "getErrors" ) );

	private Whitelist whitelist;

	private String baseURI;

	@Override
	public void initialize(SafeHtml safeHtmlAnnotation) {
		switch ( safeHtmlAnnotation.whitelistType() ) {
			case BASIC:
				whitelist = Whitelist.basic();
				break;
			case BASIC_WITH_IMAGES:
				whitelist = Whitelist.basicWithImages();
				break;
			case NONE:
				whitelist = Whitelist.none();
				break;
			case RELAXED:
				whitelist = Whitelist.relaxed();
				break;
			case SIMPLE_TEXT:
				whitelist = Whitelist.simpleText();
				break;
		}
		baseURI = safeHtmlAnnotation.baseURI();
		whitelist.addTags( safeHtmlAnnotation.additionalTags() );

		for ( SafeHtml.Tag tag : safeHtmlAnnotation.additionalTagsWithAttributes() ) {
			whitelist.addTags( tag.name() );
			if ( tag.attributes().length > 0 ) {
				whitelist.addAttributes( tag.name(), tag.attributes() );
			}

			for ( SafeHtml.Attribute attribute : tag.attributesWithProtocols() ) {
				whitelist.addAttributes( tag.name(), attribute.name() );

				if ( attribute.protocols().length > 0 ) {
					whitelist.addProtocols( tag.name(), attribute.name(), attribute.protocols() );
				}
			}
		}
	}

	@Override
	public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
		if ( value == null ) {
			return true;
		}

		// using the XML parser ensures that all elements in the input are retained, also if they actually are not allowed at the given
		// location; E.g. a <td> element isn't allowed directly within the <body> element, so it would be used by the default HTML parser.
		// we need to retain it though to apply the given white list properly; See HV-873
		Parser parser = Parser.xmlParser().setTrackErrors( MAX_TRACKED_PARSE_ERRORS );
		Document fragment = Jsoup.parse( value.toString(), baseURI, parser );

		// a malformed input (e.g. a tag which is not terminated with '>') is silently discarded by the parser and thus
		// not seen by the cleaner, while browsers would still render it, completing it with the subsequent content of
		// the page; such an input can not be considered safe
		if ( hasParseErrors( parser ) ) {
			return false;
		}

		return new Cleaner( whitelist ).isValid( getFragmentAsDocument( fragment ) );
	}

	/**
	 * Returns a document whose {@code <body>} element contains the given HTML fragment.
	 */
	private Document getFragmentAsDocument(Document fragment) {
		Document document = Document.createShell( baseURI );

		// add the fragment's nodes to the body of resulting document
		List<Node> childNodes = fragment.childNodes();
		for ( Node node : childNodes ) {
			document.body().appendChild( node.clone() );
		}

		return document;
	}

	/**
	 * Returns {@code true} if the given parser reported any error (other than the ones not denoting a malformed input)
	 * while parsing, or if the reported errors could not be inspected entirely.
	 */
	private static boolean hasParseErrors(Parser parser) {
		if ( GET_PARSE_ERRORS_METHOD == null ) {
			return true;
		}

		Collection<?> errors = (Collection<?>) ReflectionHelper.getValue( GET_PARSE_ERRORS_METHOD, parser );
		if ( errors == null ) {
			return true;
		}

		// the parser stops tracking errors once the limit is reached, so further errors may have been missed
		if ( errors.size() >= MAX_TRACKED_PARSE_ERRORS ) {
			return true;
		}

		for ( Object error : errors ) {
			if ( !( error instanceof ParseError )
					|| !SELF_CLOSING_FLAG_NOT_ACKNOWLEDGED_ERROR.equals( ( (ParseError) error ).getErrorMessage() ) ) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Runs the given privileged action, using a privileged block if required.
	 * <p>
	 * <b>NOTE:</b> This must never be changed into a publicly available method to avoid execution of arbitrary
	 * privileged actions within HV's protection domain.
	 */
	private static <T> T run(PrivilegedAction<T> action) {
		return System.getSecurityManager() != null ? AccessController.doPrivileged( action ) : action.run();
	}
}
