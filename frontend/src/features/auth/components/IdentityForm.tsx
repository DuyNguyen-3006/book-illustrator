import { useState, type FormEvent } from "react";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { messageOf } from "@/shared/lib/errors";
import { cn } from "@/lib/utils";
import { useLogin } from "../hooks/useLogin";

// Same shape the backend's IdentifyUserUseCase accepts, so the two agree on what
// "valid" means instead of the browser waving through what the API will reject.
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const FIELD_CLASSES =
  "h-11 w-full rounded-md border border-input bg-background px-3 text-sm text-foreground " +
  "placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 " +
  "focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background " +
  "aria-[invalid=true]:border-destructive";

interface FieldErrors {
  name?: string;
  email?: string;
}

export function IdentityForm({ onSignedIn }: { onSignedIn: () => void }) {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const { mutate: signIn, isPending, error } = useLogin();

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const errors: FieldErrors = {};
    if (!name.trim()) {
      errors.name = "Enter your name.";
    }
    if (!EMAIL_PATTERN.test(email.trim())) {
      errors.email = "Enter a valid email address.";
    }
    setFieldErrors(errors);
    if (errors.name || errors.email) {
      return;
    }

    signIn({ email: email.trim(), name: name.trim() }, { onSuccess: onSignedIn });
  }

  return (
    <form className="flex flex-col gap-6" onSubmit={handleSubmit} noValidate>
      <div className="flex flex-col gap-2">
        <Label htmlFor="name" className="text-sm font-medium">
          Name
        </Label>
        <input
          id="name"
          name="name"
          autoComplete="name"
          className={cn(FIELD_CLASSES)}
          value={name}
          onChange={(event) => setName(event.target.value)}
          aria-invalid={Boolean(fieldErrors.name)}
          aria-describedby={fieldErrors.name ? "name-error" : undefined}
        />
        {fieldErrors.name && (
          <p id="name-error" className="text-sm text-destructive">
            {fieldErrors.name}
          </p>
        )}
      </div>

      <div className="flex flex-col gap-2">
        <Label htmlFor="email" className="text-sm font-medium">
          Email
        </Label>
        <input
          id="email"
          name="email"
          type="email"
          autoComplete="email"
          className={cn(FIELD_CLASSES)}
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          aria-invalid={Boolean(fieldErrors.email)}
          aria-describedby={fieldErrors.email ? "email-error" : undefined}
        />
        {fieldErrors.email && (
          <p id="email-error" className="text-sm text-destructive">
            {fieldErrors.email}
          </p>
        )}
      </div>

      {error && (
        <Alert role="alert" variant="destructive" size="sm">
          <AlertDescription>{messageOf(error)}</AlertDescription>
        </Alert>
      )}

      <Button type="submit" size="lg" disabled={isPending}>
        {isPending ? "Signing in" : "Continue"}
      </Button>
    </form>
  );
}
