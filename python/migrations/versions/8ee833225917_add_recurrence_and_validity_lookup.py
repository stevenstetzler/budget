"""add_recurrence_and_validity_lookup

Revision ID: 8ee833225917
Revises: 
Create Date: 2026-04-09 22:48:21.370878

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '8ee833225917'
down_revision: Union[str, Sequence[str], None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Add recurrence support to existing schema.

    For brand-new databases the tables are created by SQLAlchemy's
    ``Base.metadata.create_all`` in main.py, so the CREATE TABLE statements
    here are guarded with ``if_not_exists=True`` / ``checkfirst=True``.
    For existing databases this migration:

    1. Adds the nullable ``recurrenceId`` column to ``receipts`` (guarded:
       skipped if the column already exists, e.g. when create_all ran first).
    2. Creates the ``recurrence`` table.
    3. Creates the ``validity_lookup`` table.
    """
    # 1. Add recurrenceId to receipts (nullable, default NULL)
    #    Guard against duplicate-column error on fresh DBs where create_all
    #    already created the column.
    bind = op.get_bind()
    inspector = sa.inspect(bind)
    if inspector.has_table('receipts'):
        existing_columns = {c['name'] for c in inspector.get_columns('receipts')}
        if 'recurrenceId' not in existing_columns:
            with op.batch_alter_table('receipts') as batch_op:
                batch_op.add_column(
                    sa.Column('recurrenceId', sa.String(), nullable=True, index=True)
                )
        # Ensure the index on receipts.recurrenceId exists (ALTER TABLE won't
        # create one automatically; it may already exist on fresh DBs).
        existing_indexes = {idx['name'] for idx in inspector.get_indexes('receipts')}
        if 'ix_receipts_recurrenceId' not in existing_indexes:
            op.create_index('ix_receipts_recurrenceId', 'receipts', ['recurrenceId'], if_not_exists=True)

    # 2. Create the recurrence table
    op.create_table(
        'recurrence',
        sa.Column('id', sa.String(), nullable=False),
        sa.Column('receiptId', sa.String(), nullable=False),
        sa.Column('frequency', sa.String(), nullable=False),
        sa.Column('startDate', sa.Integer(), nullable=False),
        sa.Column('endDate', sa.Integer(), nullable=True),
        sa.Column('dayOfPeriod', sa.Integer(), nullable=False),
        sa.PrimaryKeyConstraint('id'),
        if_not_exists=True,
    )
    op.create_index(
        op.f('ix_recurrence_id'), 'recurrence', ['id'], unique=False, if_not_exists=True
    )
    op.create_index(
        op.f('ix_recurrence_receiptId'), 'recurrence', ['receiptId'], unique=False, if_not_exists=True
    )

    # 3. Create the validity_lookup table (with unique constraint on recurrence+month)
    op.create_table(
        'validity_lookup',
        sa.Column('id', sa.String(), nullable=False),
        sa.Column('recurrenceId', sa.String(), nullable=False),
        sa.Column('targetMonth', sa.Integer(), nullable=False),
        sa.Column('isActive', sa.Boolean(), nullable=False),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('recurrenceId', 'targetMonth', name='uq_validity_lookup_recurrence_month'),
        if_not_exists=True,
    )
    op.create_index(
        op.f('ix_validity_lookup_id'), 'validity_lookup', ['id'], unique=False, if_not_exists=True
    )
    op.create_index(
        op.f('ix_validity_lookup_recurrenceId'), 'validity_lookup', ['recurrenceId'], unique=False,
        if_not_exists=True,
    )
    op.create_index(
        op.f('ix_validity_lookup_targetMonth'), 'validity_lookup', ['targetMonth'], unique=False,
        if_not_exists=True,
    )


def downgrade() -> None:
    """Remove recurrence support."""
    op.drop_index(op.f('ix_validity_lookup_targetMonth'), table_name='validity_lookup')
    op.drop_index(op.f('ix_validity_lookup_recurrenceId'), table_name='validity_lookup')
    op.drop_index(op.f('ix_validity_lookup_id'), table_name='validity_lookup')
    op.drop_table('validity_lookup')
    op.drop_index(op.f('ix_recurrence_receiptId'), table_name='recurrence')
    op.drop_index(op.f('ix_recurrence_id'), table_name='recurrence')
    op.drop_table('recurrence')
    with op.batch_alter_table('receipts') as batch_op:
        batch_op.drop_column('recurrenceId')
