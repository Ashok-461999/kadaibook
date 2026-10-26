import type { ReactNode } from "react";

interface Props {
  title: string;
  children: ReactNode;
  onClose: () => void;
  wide?: boolean;
}

export default function Modal({ title, children, onClose, wide }: Props) {
  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={onClose}>
      <section
        className={wide ? "modal wide" : "modal"}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onMouseDown={(e) => e.stopPropagation()}
      >
        <header className="modal-header">
          <h2>{title}</h2>
          <button className="icon-button" onClick={onClose} aria-label="Close">
            ×
          </button>
        </header>
        {children}
      </section>
    </div>
  );
}
