SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;

BEGIN TRY
    BEGIN TRANSACTION;

    IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'subscription_plans')
        CREATE TABLE subscription_plans(
            plan_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
            plan_code NVARCHAR(20) NOT NULL,
            display_name NVARCHAR(100) NOT NULL,
            price_vnd BIGINT NOT NULL,
            duration_days INT NULL,
            max_file_bytes BIGINT NOT NULL,
            max_documents INT NOT NULL,
            max_storage_bytes BIGINT NOT NULL,
            max_personal_workspaces INT NOT NULL,
            benefits_json NVARCHAR(MAX) NOT NULL,
            is_active BIT NOT NULL DEFAULT 1,
            created_at DATETIME2 NOT NULL DEFAULT GETDATE(),
            updated_at DATETIME2 NOT NULL DEFAULT GETDATE(),
            CONSTRAINT uq_subscription_plans_code UNIQUE(plan_code),
            CONSTRAINT chk_subscription_plans_price CHECK(price_vnd >= 0),
            CONSTRAINT chk_subscription_plans_duration CHECK(duration_days IS NULL OR duration_days > 0),
            CONSTRAINT chk_subscription_plans_quotas CHECK(
                max_file_bytes > 0 AND max_documents > 0 AND
                max_storage_bytes > 0 AND max_personal_workspaces > 0
            ),
            CONSTRAINT chk_subscription_plans_benefits_json CHECK(ISJSON(benefits_json) = 1)
        );

    IF NOT EXISTS (SELECT 1 FROM subscription_plans WHERE plan_code = 'FREE')
        INSERT INTO subscription_plans(
            plan_code, display_name, price_vnd, duration_days,
            max_file_bytes, max_documents, max_storage_bytes,
            max_personal_workspaces, benefits_json, is_active
        ) VALUES (
            'FREE', N'FREE', 0, NULL,
            10485760, 10, 104857600,
            5, N'["Tối đa 10 MB mỗi file","Tối đa 10 tài liệu","100 MB tổng dung lượng","5 Personal Workspaces"]', 1
        );

    IF NOT EXISTS (SELECT 1 FROM subscription_plans WHERE plan_code = 'PRO')
        INSERT INTO subscription_plans(
            plan_code, display_name, price_vnd, duration_days,
            max_file_bytes, max_documents, max_storage_bytes,
            max_personal_workspaces, benefits_json, is_active
        ) VALUES (
            'PRO', N'PRO', 49000, 30,
            10485760, 50, 524288000,
            10, N'["Tối đa 10 MB mỗi file","Tối đa 50 tài liệu","500 MB tổng dung lượng","10 Personal Workspace"]', 1
        );

    IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'user_subscriptions')
        CREATE TABLE user_subscriptions(
            subscription_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
            user_id UNIQUEIDENTIFIER NOT NULL,
            plan_id UNIQUEIDENTIFIER NOT NULL,
            status NVARCHAR(20) NOT NULL DEFAULT 'FREE',
            started_at DATETIME2 NULL,
            expires_at DATETIME2 NULL,
            updated_at DATETIME2 NOT NULL DEFAULT GETDATE(),
            row_version BIGINT NOT NULL DEFAULT 0,
            CONSTRAINT uq_user_subscriptions_user UNIQUE(user_id),
            CONSTRAINT chk_user_subscriptions_status CHECK(status IN('FREE','PRO_ACTIVE','PRO_EXPIRED')),
            CONSTRAINT fk_user_subscriptions_user FOREIGN KEY(user_id) REFERENCES users(user_id) ON DELETE CASCADE,
            CONSTRAINT fk_user_subscriptions_plan FOREIGN KEY(plan_id) REFERENCES subscription_plans(plan_id) ON DELETE NO ACTION
        );

    INSERT INTO user_subscriptions(user_id, plan_id, status)
    SELECT u.user_id, p.plan_id, 'FREE'
    FROM users u
    CROSS JOIN subscription_plans p
    WHERE p.plan_code = 'FREE'
      AND NOT EXISTS (SELECT 1 FROM user_subscriptions s WHERE s.user_id = u.user_id);

    IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'payment_orders')
        CREATE TABLE payment_orders(
            payment_order_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
            vnp_txn_ref NVARCHAR(100) NOT NULL,
            user_id UNIQUEIDENTIFIER NOT NULL,
            plan_id UNIQUEIDENTIFIER NOT NULL,
            plan_code_snapshot NVARCHAR(20) NOT NULL,
            amount_vnd BIGINT NOT NULL,
            duration_days INT NOT NULL,
            gateway NVARCHAR(20) NOT NULL DEFAULT 'VNPAY',
            status NVARCHAR(20) NOT NULL DEFAULT 'PENDING',
            client_ip NVARCHAR(45) NOT NULL,
            expires_at DATETIME2 NOT NULL,
            gateway_transaction_no NVARCHAR(50) NULL,
            gateway_response_code NVARCHAR(10) NULL,
            gateway_transaction_status NVARCHAR(10) NULL,
            bank_code NVARCHAR(20) NULL,
            gateway_pay_date DATETIME2 NULL,
            paid_at DATETIME2 NULL,
            activated_at DATETIME2 NULL,
            created_at DATETIME2 NOT NULL DEFAULT GETDATE(),
            updated_at DATETIME2 NOT NULL DEFAULT GETDATE(),
            row_version BIGINT NOT NULL DEFAULT 0,
            CONSTRAINT uq_payment_orders_txn_ref UNIQUE(vnp_txn_ref),
            CONSTRAINT chk_payment_orders_amount CHECK(amount_vnd > 0),
            CONSTRAINT chk_payment_orders_duration CHECK(duration_days > 0),
            CONSTRAINT chk_payment_orders_gateway CHECK(gateway IN('VNPAY')),
            CONSTRAINT chk_payment_orders_status CHECK(status IN('PENDING','PAID','FAILED','EXPIRED','CANCELLED')),
            CONSTRAINT fk_payment_orders_user FOREIGN KEY(user_id) REFERENCES users(user_id) ON DELETE NO ACTION,
            CONSTRAINT fk_payment_orders_plan FOREIGN KEY(plan_id) REFERENCES subscription_plans(plan_id) ON DELETE NO ACTION
        );

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_payment_orders_user_created')
        CREATE INDEX ix_payment_orders_user_created ON payment_orders(user_id, created_at DESC);

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_payment_orders_status_expires')
        CREATE INDEX ix_payment_orders_status_expires ON payment_orders(status, expires_at);

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'uq_payment_orders_gateway_transaction')
        CREATE UNIQUE INDEX uq_payment_orders_gateway_transaction
            ON payment_orders(gateway_transaction_no)
            WHERE gateway_transaction_no IS NOT NULL;

    IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'subscription_history')
        CREATE TABLE subscription_history(
            subscription_history_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
            user_id UNIQUEIDENTIFIER NOT NULL,
            plan_id UNIQUEIDENTIFIER NOT NULL,
            payment_order_id UNIQUEIDENTIFIER NOT NULL,
            extension_from DATETIME2 NOT NULL,
            extension_to DATETIME2 NOT NULL,
            days_added INT NOT NULL,
            amount_vnd BIGINT NOT NULL,
            paid_at DATETIME2 NOT NULL,
            created_at DATETIME2 NOT NULL DEFAULT GETDATE(),
            CONSTRAINT uq_subscription_history_order UNIQUE(payment_order_id),
            CONSTRAINT chk_subscription_history_days CHECK(days_added > 0),
            CONSTRAINT fk_subscription_history_user FOREIGN KEY(user_id) REFERENCES users(user_id) ON DELETE NO ACTION,
            CONSTRAINT fk_subscription_history_plan FOREIGN KEY(plan_id) REFERENCES subscription_plans(plan_id) ON DELETE NO ACTION,
            CONSTRAINT fk_subscription_history_order FOREIGN KEY(payment_order_id) REFERENCES payment_orders(payment_order_id) ON DELETE NO ACTION
        );

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_subscription_history_user_created')
        CREATE INDEX ix_subscription_history_user_created ON subscription_history(user_id, created_at DESC);

    IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'payment_callback_audits')
        CREATE TABLE payment_callback_audits(
            callback_audit_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
            payment_order_id UNIQUEIDENTIFIER NULL,
            vnp_txn_ref NVARCHAR(100) NULL,
            callback_source NVARCHAR(10) NOT NULL,
            checksum_valid BIT NOT NULL,
            merchant_valid BIT NULL,
            amount_valid BIT NULL,
            order_state_valid BIT NULL,
            validation_error NVARCHAR(100) NULL,
            gateway_transaction_no NVARCHAR(50) NULL,
            gateway_response_code NVARCHAR(10) NULL,
            gateway_transaction_status NVARCHAR(10) NULL,
            payload_json NVARCHAR(MAX) NOT NULL,
            merchant_rsp_code NVARCHAR(10) NULL,
            merchant_message NVARCHAR(255) NULL,
            client_ip NVARCHAR(45) NULL,
            received_at DATETIME2 NOT NULL DEFAULT GETDATE(),
            CONSTRAINT chk_payment_callback_source CHECK(callback_source IN('IPN','RETURN')),
            CONSTRAINT chk_payment_callback_payload_json CHECK(ISJSON(payload_json) = 1),
            CONSTRAINT fk_payment_callback_order FOREIGN KEY(payment_order_id) REFERENCES payment_orders(payment_order_id) ON DELETE NO ACTION
        );

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_payment_callback_order_received')
        CREATE INDEX ix_payment_callback_order_received
            ON payment_callback_audits(payment_order_id, received_at DESC);

    IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'uq_personal_workspace_owner')
        DROP INDEX uq_personal_workspace_owner ON course_workspaces;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_personal_workspace_owner')
        CREATE INDEX ix_personal_workspace_owner
            ON course_workspaces(owner_user_id, is_active)
            WHERE course_id IS NULL AND owner_user_id IS NOT NULL AND visibility = 'PRIVATE';

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
