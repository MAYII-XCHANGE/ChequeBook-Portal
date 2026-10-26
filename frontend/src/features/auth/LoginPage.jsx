import { useState } from "react";
import { Eye, EyeOff, LockKeyhole, UserRound } from "lucide-react";
import toast from "react-hot-toast";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { Logo } from "../../components/brand/Logo";
import { Button } from "../../components/ui/Button";
import { FormField, inputClass } from "../../components/ui/FormField";
import { useAuth } from "../../app/providers/AuthProvider";
import EntrustTokenModal from "./EntrustTokenModal";

export function LoginPage() {
  const {
    login,
    verifyEntrust,
    cancelEntrust,
    pendingMfa,
    isAuthenticated,
  } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(false);
  const [verifyingEntrust, setVerifyingEntrust] = useState(false);
  const [entrustError, setEntrustError] = useState("");
  if (isAuthenticated) return <Navigate to="/dashboard" replace />;

  const submit = async (event) => {
    event.preventDefault();
    const nextErrors = {};
    if (!username.trim()) nextErrors.username = "Enter your email address.";
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(username.trim())) nextErrors.username = "Enter a valid email address.";
    if (!password) nextErrors.password = "Enter your password.";
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length) return;
    setLoading(true);
    try {
      const result = await login({ username: username.trim(), password });
      if (result?.mfaRequired) {
        setPassword("");
        setEntrustError("");
        return;
      }
      toast.success("Signed in successfully.");
      navigate(location.state?.from?.pathname || "/dashboard", {
        replace: true,
      });
    } catch (error) {
      toast.error(error.message || "Sign-in failed.");
    } finally {
      setLoading(false);
    }
  };

  const verifyToken = async (token) => {
    if (verifyingEntrust) return;
    setVerifyingEntrust(true);
    setEntrustError("");
    try {
      await verifyEntrust(token);
      toast.success("Signed in successfully.");
      navigate(location.state?.from?.pathname || "/dashboard", {
        replace: true,
      });
    } catch (error) {
      setEntrustError(error.message || "Entrust verification failed.");
    } finally {
      setVerifyingEntrust(false);
    }
  };

  const cancelTokenVerification = () => {
    cancelEntrust();
    setEntrustError("");
  };

  return (
    <div className="min-h-screen bg-slate-100 lg:grid lg:grid-cols-[minmax(420px,45%)_1fr]">
      <section className="relative hidden overflow-hidden bg-ink p-12 text-white lg:flex lg:flex-col lg:justify-between">
        <div className="absolute -right-24 -top-24 h-80 w-80 rounded-full border border-white/10" />
        <div className="absolute -right-8 -top-8 h-52 w-52 rounded-full border border-brand-500/30" />
        <Logo inverse background={false} />
        <div className="relative max-w-lg">
          <h1 className="text-4xl font-bold leading-tight tracking-tight">
            Every chequebook request.
            <br />
            <span className="text-brand-500">One controlled workflow.</span>
          </h1>
          <p className="mt-5 max-w-md text-base leading-7 text-white/60">
            Initiate, validate, approve and track customer chequebook requests
            across branches from a secure operations workspace.
          </p>
        </div>
        <p className="text-xs text-white/40">
          NOVA Bank • Authorized personnel only
        </p>
      </section>
      <main className="flex min-h-screen items-center justify-center p-5 sm:p-10">
        <div className="w-full max-w-[440px]">
          <div className="mb-10 lg:hidden">
            <Logo />
          </div>
          <p className="text-xs font-bold uppercase tracking-[.18em] text-brand-700">
            Welcome back
          </p>
          <h2 className="mt-2 text-3xl font-bold tracking-tight text-ink">
            Sign in to the portal
          </h2>
          <p className="mt-2 text-sm text-slate-500">
            Use your authorized credentials.
          </p>
          <form onSubmit={submit} className="mt-8 space-y-5" noValidate>
            <FormField
              label="Email Address"
              htmlFor="username"
              required
              error={errors.username}
            >
              <div className="relative">
                <UserRound className="absolute left-3.5 top-3.5 h-4 w-4 text-slate-400" />
                <input
                  id="username"
                  type="email"
                  autoComplete="username"
                  className={`${inputClass} pl-10`}
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  aria-invalid={Boolean(errors.username)}
                />
              </div>
            </FormField>
            <FormField
              label="Password"
              htmlFor="password"
              required
              error={errors.password}
            >
              <div className="relative">
                <LockKeyhole className="absolute left-3.5 top-3.5 h-4 w-4 text-slate-400" />
                <input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  autoComplete="current-password"
                  className={`${inputClass} pl-10 pr-11`}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  aria-invalid={Boolean(errors.password)}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-3 rounded p-1 text-slate-400"
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? (
                    <EyeOff className="h-4 w-4" />
                  ) : (
                    <Eye className="h-4 w-4" />
                  )}
                </button>
              </div>
            </FormField>
            <Button type="submit" loading={loading} className="w-full">
              {loading ? "Authenticating…" : "Sign In"}
            </Button>
          </form>
        </div>
      </main>
      {pendingMfa && (
        <EntrustTokenModal
          isSubmitting={verifyingEntrust}
          error={entrustError}
          onSubmit={verifyToken}
          onCancel={cancelTokenVerification}
        />
      )}
    </div>
  );
}
