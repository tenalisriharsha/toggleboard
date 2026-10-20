import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { TargetingOperator, TargetingRule } from '../../core/models';

interface RuleRow {
  attribute: string;
  operator: TargetingOperator;
  valuesText: string;
  overrideVariantKey: string;
}

/**
 * Edits a list of targeting rules — used for both a flag's own targeting
 * rules and a segment's rules. `overrideVariantKey` only makes sense on a
 * multivariate flag's rules, so the parent opts in via `allowOverride`.
 */
@Component({
  selector: 'app-targeting-rules-editor',
  standalone: true,
  templateUrl: './targeting-rules-editor.component.html',
  styleUrl: './targeting-rules-editor.component.css',
})
export class TargetingRulesEditorComponent implements OnChanges {
  @Input() rules: TargetingRule[] = [];
  @Input() availableVariantKeys: string[] = [];
  @Input() allowOverride = false;
  @Output() rulesChange = new EventEmitter<TargetingRule[]>();

  readonly operators: TargetingOperator[] = ['EQUALS', 'IN', 'CONTAINS'];
  rows: RuleRow[] = [];

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['rules']) {
      this.rows = (this.rules ?? []).map((rule) => ({
        attribute: rule.attribute,
        operator: rule.operator,
        valuesText: (rule.values ?? []).join(', '),
        overrideVariantKey: rule.overrideVariantKey ?? '',
      }));
    }
  }

  addRule(): void {
    this.rows = [...this.rows, { attribute: '', operator: 'EQUALS', valuesText: '', overrideVariantKey: '' }];
    this.emit();
  }

  removeRule(index: number): void {
    this.rows = this.rows.filter((_, i) => i !== index);
    this.emit();
  }

  updateRow(index: number, patch: Partial<RuleRow>): void {
    this.rows = this.rows.map((row, i) => (i === index ? { ...row, ...patch } : row));
    this.emit();
  }

  private emit(): void {
    const rules: TargetingRule[] = this.rows.map((row) => ({
      attribute: row.attribute,
      operator: row.operator,
      values: row.valuesText
        .split(',')
        .map((v) => v.trim())
        .filter((v) => v.length > 0),
      overrideVariantKey: row.overrideVariantKey || null,
    }));
    this.rulesChange.emit(rules);
  }
}
