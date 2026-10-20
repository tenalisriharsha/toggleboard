import { TestBed } from '@angular/core/testing';
import { TargetingRulesEditorComponent } from './targeting-rules-editor.component';
import { TargetingRule } from '../../core/models';

describe('TargetingRulesEditorComponent', () => {
  function create(): TargetingRulesEditorComponent {
    const fixture = TestBed.createComponent(TargetingRulesEditorComponent);
    return fixture.componentInstance;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [TargetingRulesEditorComponent] });
  });

  it('seeds rows from the input rules, joining values for display', () => {
    const component = create();
    const rules: TargetingRule[] = [{ attribute: 'plan', operator: 'IN', values: ['pro', 'enterprise'], overrideVariantKey: null }];
    component.rules = rules;
    component.ngOnChanges({ rules: {} as any });

    expect(component.rows).toEqual([{ attribute: 'plan', operator: 'IN', valuesText: 'pro, enterprise', overrideVariantKey: '' }]);
  });

  it('emits a new rule list when a row is added', () => {
    const component = create();
    component.ngOnChanges({ rules: {} as any });
    let emitted: TargetingRule[] | undefined;
    component.rulesChange.subscribe((rules) => (emitted = rules));

    component.addRule();

    expect(component.rows.length).toBe(1);
    expect(emitted).toEqual([{ attribute: '', operator: 'EQUALS', values: [], overrideVariantKey: null }]);
  });

  it('splits comma-separated values and trims whitespace on update', () => {
    const component = create();
    component.ngOnChanges({ rules: {} as any });
    component.addRule();
    let emitted: TargetingRule[] | undefined;
    component.rulesChange.subscribe((rules) => (emitted = rules));

    component.updateRow(0, { attribute: 'plan', valuesText: ' pro , enterprise ,  ' });

    expect(emitted).toEqual([{ attribute: 'plan', operator: 'EQUALS', values: ['pro', 'enterprise'], overrideVariantKey: null }]);
  });

  it('removes a row by index and re-emits', () => {
    const component = create();
    component.ngOnChanges({ rules: {} as any });
    component.addRule();
    component.addRule();
    let emitted: TargetingRule[] | undefined;
    component.rulesChange.subscribe((rules) => (emitted = rules));

    component.removeRule(0);

    expect(component.rows.length).toBe(1);
    expect(emitted?.length).toBe(1);
  });

  it('includes overrideVariantKey only when a non-empty value was picked', () => {
    const component = create();
    component.ngOnChanges({ rules: {} as any });
    component.addRule();
    let emitted: TargetingRule[] | undefined;
    component.rulesChange.subscribe((rules) => (emitted = rules));

    component.updateRow(0, { overrideVariantKey: 'treatment' });
    expect(emitted?.[0].overrideVariantKey).toBe('treatment');

    component.updateRow(0, { overrideVariantKey: '' });
    expect(emitted?.[0].overrideVariantKey).toBeNull();
  });
});
