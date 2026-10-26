import { useEffect, useId, useRef, useState } from "react";
import { LoaderCircle, X } from "lucide-react";
import { validateEntrustToken } from "../validations/authValidation";
import "./EntrustTokenModal.css";

const MIN_TOKEN_LENGTH = 6;
const MAX_TOKEN_LENGTH = 10;

export default function EntrustTokenModal({
  isSubmitting,
  error,
  onSubmit,
  onCancel,
}) {
  const dialogRef = useRef(null);
  const inputRef = useRef(null);
  const [token, setToken] = useState("");
  const [validationError, setValidationError] = useState("");
  const id = useId();
  const displayedError = validationError || error;

  useEffect(() => {
    const dialog = dialogRef.current;
    const previousOverflow = document.body.style.overflow;
    dialog.showModal();
    document.body.style.overflow = "hidden";
    inputRef.current?.focus();
    return () => {
      dialog.close();
      document.body.style.overflow = previousOverflow;
    };
  }, []);

  useEffect(() => {
    if (!isSubmitting) inputRef.current?.focus();
  }, [isSubmitting]);

  const updateToken = (value) => {
    setToken(value.replace(/\D/g, "").slice(0, MAX_TOKEN_LENGTH));
    setValidationError("");
  };

  const resetToken = () => {
    setToken("");
    setValidationError("");
    inputRef.current?.focus();
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (isSubmitting) return;
    const nextError = validateEntrustToken(token);
    setValidationError(nextError);
    if (nextError) {
      inputRef.current?.focus();
      return;
    }
    try {
      await onSubmit(token);
    } finally {
      setToken("");
    }
  };

  const handleKeyDown = (event) => {
    if (event.key !== "Tab") return;
    const controls = Array.from(
      dialogRef.current.querySelectorAll(
        "button:not(:disabled), input:not(:disabled)",
      ),
    );
    const first = controls[0];
    const last = controls[controls.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last?.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first?.focus();
    }
  };

  return (
    <dialog
      ref={dialogRef}
      className="entrust-modal"
      aria-labelledby={`${id}-title`}
      aria-describedby={`${id}-description`}
      onCancel={(event) => {
        event.preventDefault();
        if (!isSubmitting) onCancel();
      }}
      onKeyDown={handleKeyDown}
    >
      <button
        type="button"
        className="entrust-modal-close"
        aria-label="Cancel Entrust verification"
        onClick={onCancel}
        disabled={isSubmitting}
      >
        <X size={20} />
      </button>
      <h2 id={`${id}-title`}>Verify OTP</h2>
      <p id={`${id}-description`}>
        Enter the 6 to 10-digit code from your Entrust authenticator
      </p>
      <form onSubmit={handleSubmit} aria-busy={isSubmitting}>
        <div className="entrust-modal-inputs">
          <input
            ref={inputRef}
            id={`${id}-token`}
            className="entrust-modal-input"
            type="text"
            inputMode="numeric"
            pattern="[0-9]{6,10}"
            minLength={MIN_TOKEN_LENGTH}
            maxLength={MAX_TOKEN_LENGTH}
            autoComplete="one-time-code"
            value={token}
            onChange={(event) => updateToken(event.target.value)}
            disabled={isSubmitting}
            aria-label="Entrust token"
            aria-invalid={Boolean(displayedError)}
            aria-describedby={displayedError ? `${id}-error` : `${id}-description`}
            placeholder="Enter your OTP"
          />
        </div>
        {displayedError && (
          <p id={`${id}-error`} className="entrust-modal-error" role="alert">
            {displayedError}
          </p>
        )}
        <p className="entrust-modal-resend">
          Need a new code?{" "}
          <button type="button" onClick={resetToken} disabled={isSubmitting}>
            Use a new token
          </button>
        </p>
        <div className="entrust-modal-actions">
          <button
            type="submit"
            className="entrust-modal-submit"
            disabled={isSubmitting
              || token.length < MIN_TOKEN_LENGTH
              || token.length > MAX_TOKEN_LENGTH}
          >
            {isSubmitting ? (
              <>
                <LoaderCircle
                  size={17}
                  className="entrust-modal-spinner"
                  aria-hidden="true"
                />{" "}
                Verifying...
              </>
            ) : (
              "Verify OTP"
            )}
          </button>
        </div>
      </form>
    </dialog>
  );
}
