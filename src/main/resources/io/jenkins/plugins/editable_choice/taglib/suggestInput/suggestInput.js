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

// Bahave just like Jenkins build-in combobox as possible.
//
// Suggest input:
// 2 state: suggesting / not-suggesting
//
// suggesting:
// * show suggest box (add 'suggesting' class to the container)
// * losing the focus: switch to not-suggesting
// * click a choice: enter the selected and switch to not-suggesting.
// * pressing up cursor: select upper, or select the lowest if not currently selected.
// * pressing down cursor: select lower, or select the top most if not currently selected.
// * pressing enter: enter the selected and switch to not-suggesting.
// * pressing tab: same to pressing enter.
// * pressing esc: switch to not-suggesting.
// * input: filter choices in configured way. select completely matching choice, deselect if none.
// * form submitting: prevent and switch to not-suggesting.
//
// not-suggesting:
// * focused: switch to suggesting.
// * pressing up cursor: select the lowest and switch to suggesting.
// * pressing down cursor: select the top most and switch to suggesting.
// * input: switch to suggesting.
//
// in any state:
// * form submitting: prevent if in restrict mode and the value isn't in choices.
//
// Jenkins supports IE11, so use legacy class definition.
// https://www.jenkins.io/doc/administration/requirements/web-browsers/
// followings are not supported in IE11:
// * arrow function
// * new style class definition (`class` keyword)
// * Array.find / Array.findIndex
document.addEventListener('DOMContentLoaded', function() {
  // options:
  //   textbox: textbox to use
  //   choices: elements to use as choices
  //   filterConfig: configurations for filtering.
  //   restrict: whether to rectrict input to be a value in choices.
  const SuggestInput =  function(container, option) {
    if (!option) {
      option = {};
    }
    this.container = container;
    this.textbox = option.textbox || this.container.querySelector('input[type="text"]');
    // workaround to make `map` applicable to NodeList. (and this works in IE)
    this.choices = Array.prototype.slice.call(option.choices || this.container.querySelectorAll('[data-value]'));
    this.choiceValues = this.choices.map(function(e) {
      return e.dataset.value;
    });
    this.filterConfig = option.filterConfig || null;
    this.restrict = option.restrict || null;
    this.currentInput = '';
    this.filter = null;
    if (this.filterConfig != null) {
      if (this.filterConfig.prefix) {
        if (this.filterConfig.caseInsensitive) {
          this.filter = function(input, testValue) {
            if (!input) { return true; }
            return testValue.toLowerCase().indexOf(input.toLowerCase()) == 0;
          }
        } else {
          this.filter = function(input, testValue) {
            if (!input) { return true; }
            return testValue.indexOf(input) == 0;
          }
        }
      } else {
        if (this.filterConfig.caseInsensitive) {
          this.filter = function(input, testValue) {
            if (!input) { return true; }
            return testValue.toLowerCase().indexOf(input.toLowerCase()) >= 0;
          }
        } else {
          this.filter = function(input, testValue) {
            if (!input) { return true; }
            return testValue.indexOf(input) >= 0;
          }
        }
      }
    }

    this.setupEvents();
    this.updateInput(true, true);
    this.checkRestriction();
  };

  SuggestInput.prototype.setupEvents = function() {
    const self = this;
    // set up choices behavior
    // * activate on mouse over
    // * enter value when clicking
    this.choices.forEach(function(e) {
      e.addEventListener('mouseenter', function() {
        self.select(e);
      });
      e.addEventListener('click', function(evt) {
        evt.stopPropagation();
        self.decide(e);
      });
    });

    // set up textbox behavir
    // * focus / blur: toggle display of choices
    // * pressing cursor keys: move active choices
    // * pressing enter / tab: input the selected value
    // * inputting: filter values
    // * updating: error check (restrict mode)
    this.textbox.addEventListener('focus', function() {
      self.startSuggesting();
    });
    this.textbox.addEventListener('blur', function() {
      // hiding the block immediately prevents 'click' for choices from firing.
      setTimeout(
        function() {
          self.stopSuggesting();
        },
        500
      );
    });
    this.textbox.addEventListener('keydown', function(evt) {
      switch (evt.keyCode) {
      case 38: // up
        self.selectUpper();
        evt.stopPropagation();
        evt.preventDefault();
        break;
      case 40: // down
        self.selectLower();
        evt.stopPropagation();
        evt.preventDefault();
        break;
      case 9: // tab
      case 13: // enter
        if (self.isSuggesting()) {
          // prevent form submit
          evt.stopPropagation();
          evt.preventDefault();
          const selected = self.getSelected();
          if (selected != null) {
            self.decide(selected);
          } else {
            self.stopSuggesting();
          }
        }
        break;
      case 27: // esc
        if (self.isSuggesting()) {
          evt.stopPropagation();
          evt.preventDefault();
          self.stopSuggesting();
        }
        break;
      }
    });
    this.textbox.addEventListener('input', function() {
      self.updateInput();
    });
    this.textbox.addEventListener('change', function() {
      self.checkRestriction();
    });

    // prevent submitting if
    // * in suggestion mode
    // * restricted and the value is not in choices
    this.textbox.form.addEventListener('submit', function(evt) {
      if (self.isRestrictionError()) {
        // prevent form submit
        evt.stopPropagation();
        evt.preventDefault();
        self.startSuggesting();
        return;
      }
      if (self.isSuggesting()) {
        // prevent form submit
        evt.stopPropagation();
        evt.preventDefault();
        self.stopSuggesting();
      }
    });
  };

  SuggestInput.prototype.startSuggesting = function() {
    if (this.isSuggesting()) {
      return;
    }
    this.updateInput(true, true);
    this.container.classList.add('suggesting');
  };

  SuggestInput.prototype.stopSuggesting = function() {
    if (!this.isSuggesting()) {
      return;
    }
    this.select(null);
    this.checkRestriction();
    this.container.classList.remove('suggesting');
  };

  SuggestInput.prototype.isSuggesting = function() {
    return this.container.classList.contains('suggesting');
  };

  SuggestInput.prototype.select = function(e) {
    this.choices.forEach(function(e) {
      e.classList.remove('active');
    });
    if (e) {
      e.classList.add('active');
    }
  };

  SuggestInput.prototype.getSelected = function() {
    const selected = this.choices.filter(function(e) {
      return e.classList.contains('active') && !e.classList.contains('filter-out');
    });
    if (selected.length <= 0) {
      return null;
    }
    return selected[0];
  };

  SuggestInput.prototype._getAvailableChoiceState = function() {
    const availableChoices = this.choices.filter(function(e) {
      return !e.classList.contains('filter-out');
    });
    let selected = null;
    availableChoices.forEach(function(e, idx) {
      if (selected == null && e.classList.contains('active')) {
        selected = idx;
      }
    });
    return {
      availableChoices: availableChoices,
      selected: selected,
    };
  };

  SuggestInput.prototype.selectUpper = function() {
    if (!this.isSuggesting()) {
      this.startSuggesting();
      this.select(null);
    }
    const choiceState = this._getAvailableChoiceState();
    if (choiceState.availableChoices.length <= 0) {
      this.select(null);
      return;
    }
    if (choiceState.selected == null || choiceState.selected <= 0) {
      this.select(choiceState.availableChoices[choiceState.availableChoices.length - 1]);
      return;
    }
    this.select(choiceState.availableChoices[choiceState.selected - 1]);
  };

  SuggestInput.prototype.selectLower = function() {
    if (!this.isSuggesting()) {
      this.startSuggesting();
      this.select(null);
    }
    const choiceState = this._getAvailableChoiceState();
    if (choiceState.availableChoices.length <= 0) {
      this.select(null);
      return;
    }
    if (choiceState.selected == null || choiceState.selected >= choiceState.availableChoices.length - 1) {
      this.select(choiceState.availableChoices[0]);
      return;
    }
    this.select(choiceState.availableChoices[choiceState.selected + 1]);
  };

  SuggestInput.prototype.decide = function(e) {
    this.textbox.value = e.dataset.value;
    this.updateInput(false, true);
    this.checkRestriction();
    this.stopSuggesting();
  };

  SuggestInput.prototype.updateInput = function(forceUpdate, suppressSuggestion) {
    const input = this.textbox.value;
    if (!forceUpdate && input === this.currentInput) {
      return;
    }
    this.currentInput = input;

    let availables = [];
    const self = this;
    if (this.filter != null) {
      this.choices.forEach(function(e) {
        if (self.filter(self.currentInput, e.dataset.value)) {
          e.classList.remove('filter-out');
          availables.push(e);
        } else {
          e.classList.add('filter-out');
          e.classList.remove('active');
        }
      });
    } else {
      availables = this.choices;
    }
    let match = null;
    availables.forEach(function(e) {
      if (match == null && e.dataset.value === self.currentInput) {
        match = e;
      }
    });
    this.select(match);
    if (!suppressSuggestion && !this.isSuggesting()) {
      this.startSuggesting();
    }
  };

  SuggestInput.prototype.isRestrictionError = function() {
    if (!this.restrict) {
      return false;
    }
    return !this.choiceValues.includes(this.textbox.value);
  };

  SuggestInput.prototype.checkRestriction = function() {
    if (!this.restrict) {
      return;
    }
    if (this.isRestrictionError()) {
      this.container.classList.add('restriction-error');
    } else {
      this.container.classList.remove('restriction-error');
    }
  };

  document.querySelectorAll('.editable-choice-suggest').forEach(function(e) {
    new SuggestInput(
      e,
      {
        textbox: e.querySelector('.editable-choice-suggest-input-block input[type="text"]'),
        choices: e.querySelectorAll('.editable-choice-suggest-choices [data-value]'),
        filterConfig: JSON.parse(e.dataset.filterConfig),
        restrict: JSON.parse(e.dataset.restrict)
      }
    );
  });
});
