/*
 * The MIT License
 *
 * Copyright (c) 2021 IKEDA Yasuyuki
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.jenkins.plugins.editable_choice;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlFormUtil;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlTextInput;
import org.htmlunit.javascript.host.event.KeyboardEvent;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.ParametersDefinitionProperty;

/**
 * Tests for behaviors in build page.
 */
public class EditableChoiceParameterDefinitionUiTest {
    private static final long JAVASCRIPT_TIMEOUT = 1000;

    @Rule
    public final JenkinsRule j = new JenkinsRule();
    private final WebClient wc = j.createWebClient();

    @Before
    public void setupWebClient() throws Exception {
        wc.setThrowExceptionOnFailingStatusCode(false);
        wc.getOptions().setPrintContentOnFailingStatusCode(false);
    }

    private HtmlPage getBuildPage(final Job<?, ?> p) throws Exception {
        return wc.getPage(p, "build?delay=0sec");
    }

    /*
    // Unfortunatelly, htmlunit doesn't simulate browser behaviors for
    // typing "ENTER" and "TAB" exactly.
    // We'll test not browser behavior but result of `preventDefault()` instead.
    private void typeEnter(final HtmlElement e) throws Exception {
        // NOTICE: this doesn't simulate actual browser behavior.
        // This doesn't trigger form submittion in Htmlunit.
        e.type(KeyboardEvent.DOM_VK_RETURN);
        // This always submit form even if `preventDefault()` called.
        e.type('\n');
    }

    private void typeTab(final HtmlElement e) throws Exception {
        // NOTICE: this doesn't simulate actual browser behavior.
        // This doesn't move focus
        e.type(KeyboardEvent.DOM_VK_TAB);
        // This doesn't move focus
        e.type('\t');
        // This doesn't trigger keydown event
        e.getHtmlPageOrNull().tabToNextElement();
    }
    */

    private void assertKeydownNotInturrupted(final HtmlElement e, final int code) throws Exception {
        final KeyboardEvent evt = new KeyboardEvent(
            e,
            KeyboardEvent.TYPE_KEY_DOWN,
            code,
            false,
            false,
            false
        );
        e.fireEvent(evt);
        assertThat(
            evt.isDefaultPrevented(),
            is(false)
        );
    }

    private void assertKeydownInturrupted(final HtmlElement e, final int code) throws Exception {
        final KeyboardEvent evt = new KeyboardEvent(
            e,
            KeyboardEvent.TYPE_KEY_DOWN,
            code,
            false,
            false,
            false
        );
        e.fireEvent(evt);
        assertThat(
            evt.isDefaultPrevented(),
            is(true)
        );
    }

    private void clickSubmit(final HtmlPage page) throws Exception {
        // j.submit() triggers submit twice.
        HtmlFormUtil.getSubmitButton(page.getFormByName("parameters")).click();
    }

    private HtmlElement getSuggestInputContainer(final HtmlPage page, final String paramName) throws Exception {
        final HtmlElement paramBlock = page.querySelector(
            String.format("[data-parameter='%s']", paramName)
        );
        return paramBlock.querySelector(".editable-choice-suggest");
    }

    private HtmlElement getSuggestInputChoicesBlock(final HtmlPage page, final String paramName) throws Exception {
        final HtmlElement paramBlock = page.querySelector(
            String.format("[data-parameter='%s']", paramName)
        );
        return paramBlock.querySelector(".editable-choice-suggest-choices");
    }

    private HtmlTextInput getSuggestInputTextbox(final HtmlPage page, final String paramName) throws Exception {
        final HtmlElement paramBlock = page.querySelector(
            String.format("[data-parameter='%s']", paramName)
        );
        return paramBlock.getOneHtmlElementByAttribute("input", "name", "value");
    }

    private HtmlElement getChoice(final HtmlPage page, final String paramName, final String value) throws Exception {
        final HtmlElement choicesBlock = getSuggestInputChoicesBlock(page, paramName);
        return choicesBlock.querySelector(String.format("[data-value='%s']", value));
    }

    private String getCurrentSelected(final HtmlPage page, final String paramName) throws Exception {
        final HtmlElement choicesBlock = getSuggestInputChoicesBlock(page, paramName);
        final List<HtmlElement> choices = choicesBlock.getByXPath("//*[@data-value]");
        final List<HtmlElement> selected = choices.stream()
            .filter(e -> hasClass(e, "active"))
            .collect(Collectors.toList());
        if (selected.size() >= 2) {
            throw new IllegalStateException(String.format(
                "More than one choices are active at the same time: %s",
                selected.stream().map(e -> e.getAttribute("data-value")).collect(Collectors.toList())
            ));
        }
        return (selected.isEmpty()) ? null : selected.get(0).getAttribute("data-value");
    }

    private List<String> getAvailableChoices(final HtmlPage page, final String paramName) throws Exception {
        final HtmlElement choicesBlock = getSuggestInputChoicesBlock(page, paramName);
        final List<HtmlElement> choices = choicesBlock.getByXPath("//*[@data-value]");
        return choices.stream()
            .filter(e -> e.isDisplayed())
            .map(e -> e.getAttribute("data-value"))
            .collect(Collectors.toList());
    }

    private boolean hasClass(final HtmlElement e, final String clazz) {
        return Arrays.asList(e.getAttribute("class").split("\\s+")).contains(clazz);
    }

    private void assertHasClass(final HtmlElement e, final String clazz) {
        assertThat(
            Arrays.asList(e.getAttribute("class").split("\\s+")),
            hasItem(is(equalTo(clazz)))
        );
    }

    private void assertNotHasClass(final HtmlElement e, final String clazz) {
        assertThat(
            Arrays.asList(e.getAttribute("class").split("\\s+")),
            not(hasItem(is(equalTo(clazz))))
        );
    }

    private void assertDisplays(final HtmlElement e) {
        assertThat(
            e.isDisplayed(),
            is(true)
        );
    }

    private void assertNotDisplays(final HtmlElement e) {
        assertThat(
            e.isDisplayed(),
            is(false)
        );
    }

    @Test
    public void testFocus() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList("Apple", "Grape", "Orange"))
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        assertHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));

        getSuggestInputTextbox(page, "PARAM1").blur();
        wc.waitForBackgroundJavaScript(JAVASCRIPT_TIMEOUT);
        assertNotHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertNotDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));

        getSuggestInputTextbox(page, "PARAM1").focus();
        assertHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
    }

    @Test
    public void testInitiallySelected() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList("Apple", "Grape", "Orange"))
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(equalTo("Apple"))
        );
    }

    @Test
    public void testInitiallyNotSelectedEmpty() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList("Apple", "Grape", "Orange"))
                .withDefaultValue("")
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(nullValue())
        );
    }

    @Test
    public void testInitiallyNotSelectedNotInChoice() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList("Apple", "Grape", "Orange"))
                .withDefaultValue("Mango")
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(nullValue())
        );
    }

    @Test
    public void testCloseSuggestion() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList("Apple", "Grape", "Orange"))
                .withDefaultValue("")
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        assertHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(nullValue())
        );

        getSuggestInputTextbox(page, "PARAM1").type(KeyboardEvent.DOM_VK_ESCAPE);
        assertNotHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertNotDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(nullValue())
        );
    }

    @Test
    public void testUpCursorInSuggestion() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList("Apple", "Grape", "Orange"))
                .withDefaultValue("")
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(nullValue())
        );

        getSuggestInputTextbox(page, "PARAM1").type(KeyboardEvent.DOM_VK_UP);
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(equalTo("Orange"))
        );

        getSuggestInputTextbox(page, "PARAM1").type(KeyboardEvent.DOM_VK_UP);
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(equalTo("Grape"))
        );

        getSuggestInputTextbox(page, "PARAM1").type(KeyboardEvent.DOM_VK_UP);
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(equalTo("Apple"))
        );

        getSuggestInputTextbox(page, "PARAM1").type(KeyboardEvent.DOM_VK_UP);
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(equalTo("Orange"))
        );
    }

    @Test
    public void testUpCursorInNonSuggestion() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList("Apple", "Grape", "Orange"))
                .withDefaultValue("")
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        getSuggestInputTextbox(page, "PARAM1").type(KeyboardEvent.DOM_VK_ESCAPE);
        assertNotHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertNotDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(nullValue())
        );

        getSuggestInputTextbox(page, "PARAM1").type(KeyboardEvent.DOM_VK_UP);
        assertHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(equalTo("Orange"))
        );
    }

    @Test
    public void testDownCursorInSuggestion() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList("Apple", "Grape", "Orange"))
                .withDefaultValue("")
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(nullValue())
        );

        getSuggestInputTextbox(page, "PARAM1").type(KeyboardEvent.DOM_VK_DOWN);
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(equalTo("Apple"))
        );

        getSuggestInputTextbox(page, "PARAM1").type(KeyboardEvent.DOM_VK_DOWN);
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(equalTo("Grape"))
        );

        getSuggestInputTextbox(page, "PARAM1").type(KeyboardEvent.DOM_VK_DOWN);
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(equalTo("Orange"))
        );

        getSuggestInputTextbox(page, "PARAM1").type(KeyboardEvent.DOM_VK_DOWN);
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(equalTo("Apple"))
        );
    }

    @Test
    public void testDownCursorInNonSuggestion() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList("Apple", "Grape", "Orange"))
                .withDefaultValue("")
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        getSuggestInputTextbox(page, "PARAM1").type(KeyboardEvent.DOM_VK_ESCAPE);
        assertNotHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertNotDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(nullValue())
        );

        getSuggestInputTextbox(page, "PARAM1").type(KeyboardEvent.DOM_VK_DOWN);
        assertHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(equalTo("Apple"))
        );
    }

    @Test
    public void testEnterInSuggestionNotSelected() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList("Apple", "Grape", "Orange"))
                .withDefaultValue("")
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        assertHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(nullValue())
        );
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo(""))
        );

        assertKeydownInturrupted(getSuggestInputTextbox(page, "PARAM1"), KeyboardEvent.DOM_VK_RETURN);
        assertNotHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertNotDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(nullValue())
        );
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo(""))
        );
    }

    @Test
    public void testEnterInSuggestionSelected() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList("Apple", "Grape", "Orange"))
                .withDefaultValue("")
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        assertHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(nullValue())
        );
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo(""))
        );

        // `mouseOver()` seems not fire `mouseenter` event
        // getChoice(page, "PARAM1", "Grape").mouseOver();
        getChoice(page, "PARAM1", "Grape").fireEvent("mouseenter");
        assertHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(equalTo("Grape"))
        );
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo(""))
        );

        assertKeydownInturrupted(getSuggestInputTextbox(page, "PARAM1"), KeyboardEvent.DOM_VK_RETURN);
        assertNotHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertNotDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(nullValue())
        );
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("Grape"))
        );
    }

    @Test
    public void testEnterInNonSuggestion() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList("Apple", "Grape", "Orange"))
                .withDefaultValue("")
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        getSuggestInputTextbox(page, "PARAM1").type("Grapefruit");
        getSuggestInputTextbox(page, "PARAM1").type(KeyboardEvent.DOM_VK_ESCAPE);
        assertNotHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertNotDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));

        assertKeydownNotInturrupted(getSuggestInputTextbox(page, "PARAM1"), KeyboardEvent.DOM_VK_RETURN);
        assertNotHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertNotDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("Grapefruit"))
        );
    }

    @Test
    public void testTabInSuggestionNotSelected() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList("Apple", "Grape", "Orange"))
                .withDefaultValue("")
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        assertHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(nullValue())
        );
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo(""))
        );

        assertKeydownInturrupted(getSuggestInputTextbox(page, "PARAM1"), KeyboardEvent.DOM_VK_TAB);
        assertNotHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertNotDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(nullValue())
        );
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo(""))
        );
    }

    @Test
    public void testTabInSuggestionSelected() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList("Apple", "Grape", "Orange"))
                .withDefaultValue("")
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        assertHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(nullValue())
        );
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo(""))
        );

        // `mouseOver()` seems not fire `mouseenter` event
        // getChoice(page, "PARAM1", "Grape").mouseOver();
        getChoice(page, "PARAM1", "Grape").fireEvent("mouseenter");
        assertHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(equalTo("Grape"))
        );
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo(""))
        );

        assertKeydownInturrupted(getSuggestInputTextbox(page, "PARAM1"), KeyboardEvent.DOM_VK_TAB);
        assertNotHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertNotDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(nullValue())
        );
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("Grape"))
        );
    }

    @Test
    public void testTagInNonSuggestion() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList("Apple", "Grape", "Orange"))
                .withDefaultValue("")
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        getSuggestInputTextbox(page, "PARAM1").type("Grapefruit");
        getSuggestInputTextbox(page, "PARAM1").type(KeyboardEvent.DOM_VK_ESCAPE);
        assertNotHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertNotDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));

        assertKeydownNotInturrupted(getSuggestInputTextbox(page, "PARAM1"), KeyboardEvent.DOM_VK_TAB);
        assertNotHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertNotDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertNotDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("Grapefruit"))
        );
    }

    @Test
    public void testClickChoice() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList("Apple", "Grape", "Orange"))
                .withDefaultValue("")
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        assertHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo(""))
        );

        getChoice(page, "PARAM1", "Grape").click();
        assertNotHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertNotDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("Grape"))
        );
    }

    @Test
    public void testFormSubmissionInSuggesting() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList("Apple", "Grape", "Orange"))
                .withDefaultValue("")
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        getSuggestInputTextbox(page, "PARAM1").type("Grapefruit");
        assertHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("Grapefruit"))
        );

        clickSubmit(page);
        assertNotHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertNotDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("Grapefruit"))
        );

        j.waitUntilNoActivity();
        assertThat(
            p.getLastBuild(),
            is(nullValue())
        );
    }

    @Test
    public void testFormSubmissionInNotSuggesting() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList("Apple", "Grape", "Orange"))
                .withDefaultValue("")
        ));
        final CaptureEnvironmentBuilder ceb = new CaptureEnvironmentBuilder();
        p.getBuildersList().add(ceb);
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        getSuggestInputTextbox(page, "PARAM1").type("Grapefruit");
        getSuggestInputTextbox(page, "PARAM1").type(KeyboardEvent.DOM_VK_ESCAPE);
        assertNotHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertNotDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("Grapefruit"))
        );

        clickSubmit(page);

        j.waitUntilNoActivity();
        j.assertBuildStatusSuccess(p.getLastBuild());
        assertThat(
            ceb.getEnvVars().get("PARAM1"),
            is(equalTo("Grapefruit"))
        );
    }

    @Test
    public void testInputInSuggestingSelect() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList("Apple", "Grape", "Orange"))
                .withDefaultValue("")
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        assertHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(nullValue())
        );

        getSuggestInputTextbox(page, "PARAM1").type("Grap");
        assertHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("Grap"))
        );
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(nullValue())
        );

        getSuggestInputTextbox(page, "PARAM1").type("e");
        assertHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("Grape"))
        );
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(equalTo("Grape"))
        );
    }

    @Test
    public void testInputInSuggestingDeselect() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList("Apple", "Grape", "Orange"))
                .withDefaultValue("")
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        assertHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(nullValue())
        );

        getSuggestInputTextbox(page, "PARAM1").type("Grape");
        assertHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("Grape"))
        );
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(equalTo("Grape"))
        );

        getSuggestInputTextbox(page, "PARAM1").type("f");
        assertHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("Grapef"))
        );
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(nullValue())
        );
    }

    @Test
    public void testInputInNotSuggesting() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList("Apple", "Grape", "Orange"))
                .withDefaultValue("")
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        getSuggestInputTextbox(page, "PARAM1").type(KeyboardEvent.DOM_VK_ESCAPE);
        assertNotHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertNotDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(nullValue())
        );

        getSuggestInputTextbox(page, "PARAM1").type("Grape");
        assertHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getCurrentSelected(page, "PARAM1"),
            is(equalTo("Grape"))
        );
    }

    @Test
    public void testNoRestrictMismatch() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList("Apple", "Grape", "Orange"))
                .withDefaultValue("Mango")
        ));
        final HtmlPage page = getBuildPage(p);

        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("Mango"))
        );
        assertNotHasClass(getSuggestInputContainer(page, "PARAM1"), "restriction-error");
    }

    @Test
    public void testRestrictMatchInitial() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList("Apple", "Grape", "Orange"))
                .withRestrict(true)
        ));
        final HtmlPage page = getBuildPage(p);

        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("Apple"))
        );
        assertNotHasClass(getSuggestInputContainer(page, "PARAM1"), "restriction-error");
    }

    @Test
    public void testRestrictMismatchInitial() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList("Apple", "Grape", "Orange"))
                .withDefaultValue("Mango")
                .withRestrict(true)
        ));
        final HtmlPage page = getBuildPage(p);

        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("Mango"))
        );
        assertHasClass(getSuggestInputContainer(page, "PARAM1"), "restriction-error");
    }

    @Test
    public void testRestrictMatchInput() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList("Apple", "Grape", "Orange"))
                .withDefaultValue("")
                .withRestrict(true)
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        getSuggestInputTextbox(page, "PARAM1").type("Grape");
        // Run restriction check
        getSuggestInputTextbox(page, "PARAM1").fireEvent("change");
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("Grape"))
        );
        assertNotHasClass(getSuggestInputContainer(page, "PARAM1"), "restriction-error");
    }

    @Test
    public void testRestrictMismatchInput() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList("Apple", "Grape", "Orange"))
                .withDefaultValue("")
                .withRestrict(true)
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        getSuggestInputTextbox(page, "PARAM1").type("Grapefruit");
        // Run restriction check
        getSuggestInputTextbox(page, "PARAM1").fireEvent("change");
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("Grapefruit"))
        );
        assertHasClass(getSuggestInputContainer(page, "PARAM1"), "restriction-error");
    }

    @Test
    public void testFormSubmissionRestrictMatch() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList("Apple", "Grape", "Orange"))
                .withDefaultValue("")
                .withRestrict(true)
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        getSuggestInputTextbox(page, "PARAM1").type("Grape");
        // Close suggestion box and run restriction check
        getSuggestInputTextbox(page, "PARAM1").type(KeyboardEvent.DOM_VK_ESCAPE);
        assertNotHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertNotDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("Grape"))
        );
        assertNotHasClass(getSuggestInputContainer(page, "PARAM1"), "restriction-error");

        clickSubmit(page);

        j.waitUntilNoActivity();
        j.assertBuildStatusSuccess(p.getLastBuild());
    }

    @Test
    public void testFormSubmissionRestrictMismatch() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList("Apple", "Grape", "Orange"))
                .withDefaultValue("")
                .withRestrict(true)
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        getSuggestInputTextbox(page, "PARAM1").type("Grapef");
        // Close suggestion box and run restriction check
        getSuggestInputTextbox(page, "PARAM1").type(KeyboardEvent.DOM_VK_ESCAPE);
        assertNotHasClass(getSuggestInputContainer(page, "PARAM1"), "suggesting");
        assertNotDisplays(getSuggestInputChoicesBlock(page, "PARAM1"));
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("Grapef"))
        );
        assertHasClass(getSuggestInputContainer(page, "PARAM1"), "restriction-error");

        clickSubmit(page);

        j.waitUntilNoActivity();
        assertThat(
            p.getLastBuild(),
            is(nullValue())
        );
    }

    @Test
    public void testNoFilterInitial() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )).withDefaultValue("App")
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("App"))
        );
        assertThat(
            getAvailableChoices(page, "PARAM1"),
            is(equalTo(
                Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )
            ))
        );
    }

    @Test
    public void testNoFilterInitialEmpty() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )).withDefaultValue("")
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo(""))
        );
        assertThat(
            getAvailableChoices(page, "PARAM1"),
            is(equalTo(
                Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )
            ))
        );
    }

    @Test
    public void testNoFilterInput() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )).withDefaultValue("")
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        getSuggestInputTextbox(page, "PARAM1").type("App");
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("App"))
        );
        assertThat(
            getAvailableChoices(page, "PARAM1"),
            is(equalTo(
                Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )
            ))
        );
    }

    @Test
    public void testNoFilterInputEmpty() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )).withDefaultValue("App")
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        getSuggestInputTextbox(page, "PARAM1").setSelectionStart(0);
        getSuggestInputTextbox(page, "PARAM1").setSelectionEnd(
            getSuggestInputTextbox(page, "PARAM1").getValue().length()
        );
        getSuggestInputTextbox(page, "PARAM1").type(KeyboardEvent.DOM_VK_DELETE);
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo(""))
        );
        assertThat(
            getAvailableChoices(page, "PARAM1"),
            is(equalTo(
                Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )
            ))
        );
    }

    @Test
    public void testFilterNoPrefixCaseSensitiveInitial() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )).withDefaultValue("App")
                .withFilterConfig(new FilterConfig()
                    .withPrefix(false)
                    .withCaseInsensitive(false)
                )
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("App"))
        );
        assertThat(
            getAvailableChoices(page, "PARAM1"),
            is(equalTo(
                Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "Green Apple"
                )
            ))
        );
    }

    @Test
    public void testFilterNoPrefixCaseSensitiveInitialEmpty() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )).withDefaultValue("")
                .withFilterConfig(new FilterConfig()
                    .withPrefix(false)
                    .withCaseInsensitive(false)
                )
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo(""))
        );
        assertThat(
            getAvailableChoices(page, "PARAM1"),
            is(equalTo(
                Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )
            ))
        );
    }

    @Test
    public void testFilterNoPrefixCaseSensitiveInput() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )).withDefaultValue("")
                .withFilterConfig(new FilterConfig()
                    .withPrefix(false)
                    .withCaseInsensitive(false)
                )
    ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        getSuggestInputTextbox(page, "PARAM1").type("App");
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("App"))
        );
        assertThat(
            getAvailableChoices(page, "PARAM1"),
            is(equalTo(
                Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "Green Apple"
                )
            ))
        );
    }

    @Test
    public void testFilterNoPrefixCaseSensitiveInputEmpty() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )).withDefaultValue("App")
                .withFilterConfig(new FilterConfig()
                    .withPrefix(false)
                    .withCaseInsensitive(false)
                )
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        getSuggestInputTextbox(page, "PARAM1").setSelectionStart(0);
        getSuggestInputTextbox(page, "PARAM1").setSelectionEnd(
            getSuggestInputTextbox(page, "PARAM1").getValue().length()
        );
        getSuggestInputTextbox(page, "PARAM1").type(KeyboardEvent.DOM_VK_DELETE);
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo(""))
        );
        assertThat(
            getAvailableChoices(page, "PARAM1"),
            is(equalTo(
                Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )
            ))
        );
    }

    @Test
    public void testFilterNoPrefixCaseInsensitiveInitial() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )).withDefaultValue("App")
                .withFilterConfig(new FilterConfig()
                    .withPrefix(false)
                    .withCaseInsensitive(true)
                )
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("App"))
        );
        assertThat(
            getAvailableChoices(page, "PARAM1"),
            is(equalTo(
                Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Green Apple",
                    "Pineapple"
                )
            ))
        );
    }

    @Test
    public void testFilterNoPrefixCaseInsensitiveInitialEmpty() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )).withDefaultValue("")
                .withFilterConfig(new FilterConfig()
                    .withPrefix(false)
                    .withCaseInsensitive(true)
                )
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo(""))
        );
        assertThat(
            getAvailableChoices(page, "PARAM1"),
            is(equalTo(
                Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )
            ))
        );
    }

    @Test
    public void testFilterNoPrefixCaseInsensitiveInput() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )).withDefaultValue("")
                .withFilterConfig(new FilterConfig()
                    .withPrefix(false)
                    .withCaseInsensitive(true)
                )
    ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        getSuggestInputTextbox(page, "PARAM1").type("App");
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("App"))
        );
        assertThat(
            getAvailableChoices(page, "PARAM1"),
            is(equalTo(
                Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Green Apple",
                    "Pineapple"
                )
            ))
        );
    }

    @Test
    public void testFilterNoPrefixCaseInsensitiveInputEmpty() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )).withDefaultValue("App")
                .withFilterConfig(new FilterConfig()
                    .withPrefix(false)
                    .withCaseInsensitive(true)
                )
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        getSuggestInputTextbox(page, "PARAM1").setSelectionStart(0);
        getSuggestInputTextbox(page, "PARAM1").setSelectionEnd(
            getSuggestInputTextbox(page, "PARAM1").getValue().length()
        );
        getSuggestInputTextbox(page, "PARAM1").type(KeyboardEvent.DOM_VK_DELETE);
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo(""))
        );
        assertThat(
            getAvailableChoices(page, "PARAM1"),
            is(equalTo(
                Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )
            ))
        );
    }

    @Test
    public void testFilterPrefixCaseSensitiveInitial() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )).withDefaultValue("App")
                .withFilterConfig(new FilterConfig()
                    .withPrefix(true)
                    .withCaseInsensitive(false)
                )
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("App"))
        );
        assertThat(
            getAvailableChoices(page, "PARAM1"),
            is(equalTo(
                Arrays.asList(
                    "Apple",
                    "Apple Mango"
                )
            ))
        );
    }

    @Test
    public void testFilterPrefixCaseSensitiveInitialEmpty() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )).withDefaultValue("")
                .withFilterConfig(new FilterConfig()
                    .withPrefix(true)
                    .withCaseInsensitive(false)
                )
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo(""))
        );
        assertThat(
            getAvailableChoices(page, "PARAM1"),
            is(equalTo(
                Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )
            ))
        );
    }

    @Test
    public void testFilterPrefixCaseSensitiveInput() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )).withDefaultValue("")
                .withFilterConfig(new FilterConfig()
                    .withPrefix(true)
                    .withCaseInsensitive(false)
                )
    ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        getSuggestInputTextbox(page, "PARAM1").type("App");
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("App"))
        );
        assertThat(
            getAvailableChoices(page, "PARAM1"),
            is(equalTo(
                Arrays.asList(
                    "Apple",
                    "Apple Mango"
                )
            ))
        );
    }

    @Test
    public void testFilterPrefixCaseSensitiveInputEmpty() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )).withDefaultValue("App")
                .withFilterConfig(new FilterConfig()
                    .withPrefix(true)
                    .withCaseInsensitive(false)
                )
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        getSuggestInputTextbox(page, "PARAM1").setSelectionStart(0);
        getSuggestInputTextbox(page, "PARAM1").setSelectionEnd(
            getSuggestInputTextbox(page, "PARAM1").getValue().length()
        );
        getSuggestInputTextbox(page, "PARAM1").type(KeyboardEvent.DOM_VK_DELETE);
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo(""))
        );
        assertThat(
            getAvailableChoices(page, "PARAM1"),
            is(equalTo(
                Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )
            ))
        );
    }

    @Test
    public void testFilterPrefixCaseInsensitiveInitial() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )).withDefaultValue("App")
                .withFilterConfig(new FilterConfig()
                    .withPrefix(true)
                    .withCaseInsensitive(true)
                )
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("App"))
        );
        assertThat(
            getAvailableChoices(page, "PARAM1"),
            is(equalTo(
                Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application"
                )
            ))
        );
    }

    @Test
    public void testFilterPrefixCaseInsensitiveInitialEmpty() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )).withDefaultValue("")
                .withFilterConfig(new FilterConfig()
                    .withPrefix(true)
                    .withCaseInsensitive(true)
                )
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo(""))
        );
        assertThat(
            getAvailableChoices(page, "PARAM1"),
            is(equalTo(
                Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )
            ))
        );
    }

    @Test
    public void testFilterPrefixCaseInsensitiveInput() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )).withDefaultValue("")
                .withFilterConfig(new FilterConfig()
                    .withPrefix(true)
                    .withCaseInsensitive(true)
                )
    ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        getSuggestInputTextbox(page, "PARAM1").type("App");
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo("App"))
        );
        assertThat(
            getAvailableChoices(page, "PARAM1"),
            is(equalTo(
                Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application"
                )
            ))
        );
    }

    @Test
    public void testFilterPrefixCaseInsensitiveInputEmpty() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
            new EditableChoiceParameterDefinition("PARAM1")
                .withChoices(Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )).withDefaultValue("App")
                .withFilterConfig(new FilterConfig()
                    .withPrefix(true)
                    .withCaseInsensitive(true)
                )
        ));
        final HtmlPage page = getBuildPage(p);

        getSuggestInputTextbox(page, "PARAM1").focus();
        getSuggestInputTextbox(page, "PARAM1").setSelectionStart(0);
        getSuggestInputTextbox(page, "PARAM1").setSelectionEnd(
            getSuggestInputTextbox(page, "PARAM1").getValue().length()
        );
        getSuggestInputTextbox(page, "PARAM1").type(KeyboardEvent.DOM_VK_DELETE);
        assertThat(
            getSuggestInputTextbox(page, "PARAM1").getValue(),
            is(equalTo(""))
        );
        assertThat(
            getAvailableChoices(page, "PARAM1"),
            is(equalTo(
                Arrays.asList(
                    "Apple",
                    "Apple Mango",
                    "application",
                    "Grape",
                    "Green Apple",
                    "Pineapple"
                )
            ))
        );
    }
}
