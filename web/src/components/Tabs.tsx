import { useRef, useState } from 'react';

export interface TabItem {
  id: string;
  label: string;
  content: React.ReactNode;
}

interface TabsProps {
  label: string;
  items: TabItem[];
}

export function Tabs({ label, items }: TabsProps) {
  const [selectedId, setSelectedId] = useState(items[0]?.id ?? '');
  const tabRefs = useRef<Array<HTMLButtonElement | null>>([]);

  function moveFocus(currentIndex: number, direction: -1 | 1) {
    const nextIndex = (currentIndex + direction + items.length) % items.length;
    const nextTab = items[nextIndex];
    if (!nextTab) return;
    setSelectedId(nextTab.id);
    tabRefs.current[nextIndex]?.focus();
  }

  return (
    <section>
      <div className="tabs" role="tablist" aria-label={label}>
        {items.map((item, index) => (
          <button
            aria-controls={`${item.id}-panel`}
            aria-selected={selectedId === item.id}
            className="tabs__tab"
            id={`${item.id}-tab`}
            key={item.id}
            onClick={() => setSelectedId(item.id)}
            onKeyDown={(event) => {
              if (event.key === 'ArrowRight') {
                event.preventDefault();
                moveFocus(index, 1);
              } else if (event.key === 'ArrowLeft') {
                event.preventDefault();
                moveFocus(index, -1);
              } else if (event.key === 'Home') {
                event.preventDefault();
                moveFocus(-1, 1);
              } else if (event.key === 'End') {
                event.preventDefault();
                moveFocus(0, -1);
              }
            }}
            ref={(element) => {
              tabRefs.current[index] = element;
            }}
            role="tab"
            tabIndex={selectedId === item.id ? 0 : -1}
            type="button"
          >
            {item.label}
          </button>
        ))}
      </div>
      {items.map((item) => (
        <div
          aria-labelledby={`${item.id}-tab`}
          className="tabs__panel"
          hidden={selectedId !== item.id}
          id={`${item.id}-panel`}
          key={item.id}
          role="tabpanel"
          tabIndex={0}
        >
          {item.content}
        </div>
      ))}
    </section>
  );
}
