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

import java.io.Serializable;

import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;


/**
 * FilterConfig holds configurations how to filter values for the input.
 */
public class FilterConfig extends AbstractDescribableImpl<FilterConfig> implements Serializable {
    private static final long serialVersionUID = 6989114009969654271L;

    private boolean prefix = false;
    private boolean caseInsensitive = false;

    /**
     * ctor.
     */
    @DataBoundConstructor
    public FilterConfig() {
    }

    /**
     * @param prefix whether to filter values only with prefixes
     */
    @DataBoundSetter
    public void setPrefix(final boolean prefix) {
        this.prefix = prefix;
    }

    /**
     * @return whether to filter values only with prefixes
     */
    public boolean isPrefix() {
        return prefix;
    }

    /**
     * @param prefix whether to filter values only with prefixes
     * @return this instance
     */
    public FilterConfig withPrefix(final boolean prefix) {
        setPrefix(prefix);
        return this;
    }

    /**
     * @param caseInsensitive whether to match in case insensitive
     */
    @DataBoundSetter
    public void setCaseInsensitive(final boolean caseInsensitive) {
        this.caseInsensitive = caseInsensitive;
    }

    /**
     * @return whether to match in case insensitive
     */
    public boolean isCaseInsensitive() {
        return caseInsensitive;
    }

    /**
     * @param caseInsensitive whether to match in case insensitive
     * @return this instance
     */
    public FilterConfig withCaseInsensitive(final boolean caseInsensitive) {
        setCaseInsensitive(caseInsensitive);
        return this;
    }

    /**
     * @return json replresentation for this configuration
     */
    @Restricted(NoExternalUse.class) // used only for the view.
    public String toJson() {
        return String.format(
            "{\"prefix\": %s, \"caseInsensitive\": %s}",
            Boolean.toString(isPrefix()),
            Boolean.toString(isCaseInsensitive())
        );
    }

    /**
     * Descriptor for {@link FilterConfig}.
     */
    @Symbol("filterConfig")
    @Extension
    public static class DescriptorImpl extends Descriptor<FilterConfig> {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.FilterConfig_DisplayName();
        }
    }
}
