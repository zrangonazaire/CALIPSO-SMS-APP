import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { paginationBounds } from '../../core/models/pagination';

@Component({
  selector: 'app-pagination',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (total() > 0) {
      <nav [attr.aria-label]="'Pagination des ' + label() + (position() ? ', ' + position() : '')">
        <p [attr.role]="position() === 'en bas' ? null : 'status'" [attr.aria-live]="position() === 'en bas' ? null : 'polite'" aria-atomic="true">{{ bounds().start }}–{{ bounds().end }} sur {{ total() }} {{ label() }}</p>
        <div class="controls">
          <button type="button" (click)="pageChange.emit(1)" [disabled]="disabled() || bounds().page === 1" [attr.aria-label]="'Première page des ' + label()">«</button>
          <button type="button" (click)="pageChange.emit(bounds().page - 1)" [disabled]="disabled() || bounds().page === 1" [attr.aria-label]="'Page précédente des ' + label()">Précédent</button>
          <span>Page {{ bounds().page }} / {{ bounds().pageCount }}</span>
          <button type="button" (click)="pageChange.emit(bounds().page + 1)" [disabled]="disabled() || bounds().page === bounds().pageCount" [attr.aria-label]="'Page suivante des ' + label()">Suivant</button>
          <button type="button" (click)="pageChange.emit(bounds().pageCount)" [disabled]="disabled() || bounds().page === bounds().pageCount" [attr.aria-label]="'Dernière page des ' + label()">»</button>
        </div>
      </nav>
    }
  `,
  styles: `
    :host { display: block; margin-block: 1rem .25rem; }
    nav { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: .6rem; font-size: .82rem; color: #334155; }
    p { margin: 0; }
    .controls { display: flex; align-items: center; flex-wrap: wrap; gap: .35rem; }
    button { background: #fff; color: #1e40af; border: 1px solid #94a3b8; padding: .45rem .6rem; border-radius: .45rem; min-height: 36px; }
    button:disabled { color: #64748b; background: #f1f5f9; border-color: #cbd5e1; cursor: default; }
    button:focus-visible { outline: 3px solid #1d4ed8; outline-offset: 2px; }
    span { padding-inline: .3rem; white-space: nowrap; }
  `,
})
export class Pagination {
  readonly page = input(1);
  readonly total = input.required<number>();
  readonly pageSize = input(25);
  readonly label = input('messages');
  readonly position = input('');
  readonly disabled = input(false);
  readonly pageChange = output<number>();
  readonly bounds = computed(() => paginationBounds(this.total(), this.page(), this.pageSize()));
}
