import { Navigate, Route, Routes } from "react-router-dom";
import { IdentityPage } from "@/features/auth/pages/IdentityPage";
import { ProtectedRoute } from "./ProtectedRoute";

/** Screens land here one issue at a time — #21 project list, #22 new project, #23 detail. */
export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<IdentityPage />} />
      <Route
        path="/projects"
        element={
          <ProtectedRoute>
            <main className="mx-auto w-full max-w-5xl px-6 py-12">
              <h1 className="text-2xl font-semibold tracking-tight">Your projects</h1>
            </main>
          </ProtectedRoute>
        }
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
