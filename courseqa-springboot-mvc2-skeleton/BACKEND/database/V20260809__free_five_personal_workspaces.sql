SET XACT_ABORT ON;
SET NOCOUNT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    UPDATE subscription_plans
    SET max_personal_workspaces = 5,
        benefits_json = N'["Tối đa 10 MB mỗi file","Tối đa 10 tài liệu","100 MB tổng dung lượng","5 Personal Workspaces"]',
        updated_at = GETDATE()
    WHERE plan_code = 'FREE';

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
